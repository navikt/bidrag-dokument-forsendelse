package no.nav.bidrag.dokument.forsendelse.hendelse

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.commons.security.SikkerhetsKontekst.medApplikasjonKontekst
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.model.KunneIkkeLeseMeldingFraHendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.service.DistribusjonService
import no.nav.bidrag.dokument.forsendelse.service.FORSENDELSE_APP_ID
import no.nav.bidrag.dokument.forsendelse.service.FerdigstillForsendelseService
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import no.nav.bidrag.dokument.forsendelse.utvidelser.kanDistribueres
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import no.nav.bidrag.transport.dokument.DokumentHendelse
import no.nav.bidrag.transport.dokument.DokumentHendelseType
import no.nav.bidrag.transport.dokument.DokumentStatusDto
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Component
class DokumentHendelseLytter(
    val objectMapper: ObjectMapper,
    val bidragDokumentConsumer: BidragDokumentConsumer,
    val dokumentTjeneste: DokumentTjeneste,
    val journalpostKafkaHendelseProdusent: JournalpostKafkaHendelseProdusent,
    val ferdigstillForsendelseService: FerdigstillForsendelseService,
    val distribusjonService: DistribusjonService,
    @Value("\${SYNKRONISER_STATUS_DOKUMENTER_ENABLED:false}") private val synkroniserDokumentStatusEnabled: Boolean,
) {
    /**
     * Sjekker om dokumenter som har status UNDER_REDIGERING er ferdigstilt eller ikke og ferdigstiller dokumentet hvis de er det
     * Denne feilen kan oppstå hvis kvittering fra brevserver ikke blir sendt på ritkig måte pga feil i verdikjeden.
     */
    @Scheduled(cron = "\${SYNKRONISER_STATUS_DOKUMENTER_CRON}")
    @SchedulerLock(name = "oppdaterStatusPaFerdigstilteDokumenter", lockAtLeastFor = "10m")
    @Transactional
    fun oppdaterStatusPaFerdigstilteDokumenterSkeduler() {
        oppdaterStatusPaFerdigstilteDokumenter(100)
    }

    fun oppdaterStatusPaFerdigstilteDokumenter(
        limit: Int = 100,
        afterDate: LocalDateTime? = null,
        beforeDate: LocalDateTime? = null,
    ): List<Dokument> {
        val dokumenter = dokumentTjeneste.hentDokumenterSomErUnderRedigering(limit, afterDate, beforeDate)
        log.info { "Hentet ${dokumenter.size} dokumenter som skal sjekkes om er ferdigstilt" }

        return dokumenter.flatMap {
            sjekkOmDokumentErFerdigstiltOgOppdaterStatus(it, synkroniserDokumentStatusEnabled)
        }
    }

    fun sjekkOmDokumentErFerdigstiltOgOppdaterStatus(
        dokument: Dokument,
        oppdaterStatus: Boolean,
    ): List<Dokument> {
        try {
            if (dokument.dokumentStatus == DokumentStatus.FERDIGSTILT) {
                log.info { "Dokument ${dokument.dokumentreferanse} er allerede ferdigstilt" }
                return emptyList()
            }

            log.info { "Sjekker om dokument ${dokument.dokumentreferanse} er ferdigstilt" }
            val erFerdigstilt = bidragDokumentConsumer.erFerdigstilt(dokument.dokumentreferanse)

            log.info {
                if (erFerdigstilt) {
                    "Dokument ${dokument.dokumentreferanse} er ferdigstilt. Oppdaterer status"
                } else {
                    "Dokument ${dokument.dokumentreferanse} er ikke ferdigstilt. Ignorerer dokument"
                }
            }
            if (erFerdigstilt && oppdaterStatus) {
                val dokumenterForReferanse = dokumentTjeneste.hentDokumenterMedReferanse(dokument.dokumentreferanse)
                val oppdaterteDokumenter =
                    dokumenterForReferanse.map { dokumentForRef ->
                        dokumentTjeneste.lagreDokument(
                            dokumentForRef.copy(
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                ferdigstiltTidspunkt = LocalDateTime.now(),
                                ferdigstiltAvIdent = FORSENDELSE_APP_ID,
                            ),
                        )
                    }
                sendJournalposthendelseHvisKlarForDistribusjon(oppdaterteDokumenter)
                ferdigstillHvisForsendelseErNotat(oppdaterteDokumenter)
                return oppdaterteDokumenter
            } else if (erFerdigstilt) {
                log.info {
                    "Dokument ${dokument.dokumentreferanse} med forsendelseid ${dokument.forsendelse.forsendelseId} " +
                        "har status ${dokument.dokumentStatus} men er ferdigstilt. " +
                        "Gjør ingen endring fordi synkronisering egenskap er ikke skrudd på"
                }
            }
            return listOf(dokument)
        } catch (e: Exception) {
            log.error(e) { "Det skjedde en feil ved oppdatering av status på dokument ${dokument.dokumentreferanse}" }
            return emptyList()
        }
    }

    @KafkaListener(groupId = "bidrag-dokument-forsendelse", topics = ["\${TOPIC_DOKUMENT}"])
    fun prossesserDokumentHendelse(melding: ConsumerRecord<String, String>) {
        val hendelse = tilDokumentHendelseObjekt(melding)

        if (hendelse.hendelseType == DokumentHendelseType.BESTILLING) return

        log.info {
            "Mottok hendelse for dokumentreferanse ${hendelse.dokumentreferanse} med status ${hendelse.status}, " +
                "arkivsystem ${hendelse.arkivSystem} og hendelsetype ${hendelse.hendelseType}"
        }

        val dokumenter = dokumentTjeneste.hentDokumenterMedReferanse(hendelse.dokumentreferanse)

        val oppdaterteDokumenter =
            dokumenter.map {
                log.info {
                    "Oppdaterer dokument ${it.dokumentId} med dokumentreferanse ${it.dokumentreferanse} " +
                        "og journalpostid ${it.journalpostId} " +
                        "fra forsendelse ${it.forsendelse.forsendelseId} med informasjon fra hendelse"
                }
                dokumentTjeneste.lagreDokument(
                    it.copy(
                        arkivsystem =
                            if (it.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
                                it.arkivsystem
                            } else {
                                when (hendelse.arkivSystem) {
                                    DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                                    else -> it.arkivsystem
                                }
                            },
                        dokumentStatus =
                            when (hendelse.status) {
                                DokumentStatusDto.UNDER_REDIGERING -> DokumentStatus.UNDER_REDIGERING
                                DokumentStatusDto.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
                                DokumentStatusDto.AVBRUTT -> DokumentStatus.AVBRUTT
                                else -> it.dokumentStatus
                            },
                        metadata =
                            run {
                                val metadata = it.metadata
                                if (erKvitteringForProdusertDokument(it, hendelse)) {
                                    metadata.lagreProdusertTidspunkt(LocalDateTime.now())
                                }
                                metadata.copy()
                            },
                        ferdigstiltTidspunkt = if (hendelse.status == DokumentStatusDto.FERDIGSTILT) LocalDateTime.now() else null,
                        ferdigstiltAvIdent = if (hendelse.status == DokumentStatusDto.FERDIGSTILT) FORSENDELSE_APP_ID else null,
                    ),
                )
            }

        sendJournalposthendelseHvisKlarForDistribusjon(oppdaterteDokumenter)
        ferdigstillHvisForsendelseErNotat(oppdaterteDokumenter)
        distribuerHvisForsendelseSkalAutomatiskDistribueres(oppdaterteDokumenter)
    }

    private fun erKvitteringForProdusertDokument(
        dokument: Dokument,
        dokumentHendelse: DokumentHendelse,
    ): Boolean =
        dokument.dokumentStatus == DokumentStatus.UNDER_PRODUKSJON &&
            (
                dokumentHendelse.status == DokumentStatusDto.UNDER_REDIGERING ||
                    dokumentHendelse.status == DokumentStatusDto.FERDIGSTILT
            )

    private fun sendJournalposthendelseHvisKlarForDistribusjon(dokumenter: List<Dokument>) {
        dokumenter.forEach {
            if (it.forsendelse.kanDistribueres()) {
                journalpostKafkaHendelseProdusent.publiserForsendelse(it.forsendelse)
            }
        }
    }

    private fun distribuerHvisForsendelseSkalAutomatiskDistribueres(dokumenter: List<Dokument>) {
        dokumenter.forEach {
            val forsendelse = it.forsendelse

            if (forsendelse.metadata?.skalDistribueresAutomatisk() == true && forsendelse.dokumenter.erAlleFerdigstilt) {
                medApplikasjonKontekst {
                    log.info {
                        "Alle dokumenter i forsendelse ${forsendelse.forsendelseId} ferdigstilt. " +
                            "Forsendelse er markert til å distribueres automatisk. Distribuerer forsendelse"
                    }
                    try {
                        distribusjonService.distribuer(
                            forsendelse.forsendelseId!!,
                            batchId = forsendelse.batchId,
                        )
                    } catch (e: Exception) {
                        log.error(e) { "Kunne ikke distribuere forsendelse ${it.forsendelseId}." }
                    }
                }
            }
        }
    }

    private fun ferdigstillHvisForsendelseErNotat(dokumenter: List<Dokument>) {
        dokumenter.forEach {
            val forsendelse = it.forsendelse

            if (forsendelse.forsendelseType == ForsendelseType.NOTAT && forsendelse.dokumenter.erAlleFerdigstilt) {
                medApplikasjonKontekst {
                    log.info {
                        "Alle dokumenter i forsendelse ${forsendelse.forsendelseId} " +
                            "med type NOTAT er ferdigstilt. Ferdigstiller forsendelse."
                    }
                    try {
                        ferdigstillForsendelseService.ferdigstillForsendelse(forsendelse.forsendelseId!!)
                    } catch (e: Exception) {
                        log.error(e) { "Kunne ikke ferdigstille forsendelse ${it.forsendelseId}." }
                    }
                }
            }
        }
    }

    private fun tilDokumentHendelseObjekt(melding: ConsumerRecord<String, String>): DokumentHendelse {
        try {
            return objectMapper.readValue(melding.value(), DokumentHendelse::class.java)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved konverting av melding fra hendelse", e)
            throw KunneIkkeLeseMeldingFraHendelse(e.message, e)
        }
    }
}
