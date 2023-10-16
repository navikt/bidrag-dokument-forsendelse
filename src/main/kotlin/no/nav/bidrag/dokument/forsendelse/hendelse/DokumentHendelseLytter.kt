package no.nav.bidrag.dokument.forsendelse.hendelse

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.commons.security.SikkerhetsKontekst.medApplikasjonKontekst
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.DokumentHendelse
import no.nav.bidrag.dokument.dto.DokumentHendelseType
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.model.KunneIkkeLeseMeldingFraHendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.service.FORSENDELSE_APP_ID
import no.nav.bidrag.dokument.forsendelse.service.FerdigstillForsendelseService
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import no.nav.bidrag.dokument.forsendelse.utvidelser.kanDistribueres
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
    @Value("\${SYNKRONISER_STATUS_DOKUMENTER_ENABLED:false}") private val synkroniserDokumentStatusEnabled: Boolean
) {

    /**
     * Sjekker om dokumenter som har status UNDER_REDIGERING er ferdigstilt eller ikke og ferdigstiller dokumentet hvis de er det
     * Denne feilen kan oppstå hvis kvittering fra brevserver ikke blir sendt på ritkig måte pga feil i verdikjeden.
     */
    @Scheduled(cron = "\${SYNKRONISER_STATUS_DOKUMENTER_CRON}")
    @SchedulerLock(name = "oppdaterStatusPaFerdigstilteDokumenter", lockAtLeastFor = "10m")
    @Transactional
    fun oppdaterStatusPaFerdigstilteDokumenter() {
        val dokumenter = dokumentTjeneste.hentDokumenterSomErUnderRedigering(100)
        log.info { "Hentet ${dokumenter.size} dokumenter som skal sjekkes om er ferdigstilt" }

        dokumenter.forEach {
            log.info { "Sjekker om dokument ${it.dokumentreferanse} er ferdigstilt" }
            try {
                val erFerdigstilt = bidragDokumentConsumer.erFerdigstilt(it.dokumentreferanse)

                log.info {
                    if (erFerdigstilt) "Dokument ${it.dokumentreferanse} er ferdigstilt. Oppdaterer status"
                    else "Dokument ${it.dokumentreferanse} er ikke ferdigstilt. Ignorerer dokument"
                }
                if (erFerdigstilt && synkroniserDokumentStatusEnabled) {
                    val dokumenterForReferanse = dokumentTjeneste.hentDokumenterMedReferanse(it.dokumentreferanse)
                    val oppdaterteDokumenter = dokumenterForReferanse.map { dokument ->
                        dokumentTjeneste.lagreDokument(
                            dokument.copy(
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                ferdigstiltTidspunkt = LocalDateTime.now(),
                                ferdigstiltAvIdent = FORSENDELSE_APP_ID
                            )
                        )
                    }
                    sendJournalposthendelseHvisKlarForDistribusjon(oppdaterteDokumenter)
                    ferdigstillHvisForsendelseErNotat(oppdaterteDokumenter)
                } else if (erFerdigstilt) {
                    log.info { "Gjør ingen endring fordi synkronisering egenskap er ikke skrudd på: synkroniserDokumentStatusEnabled = $synkroniserDokumentStatusEnabled" }
                }
            } catch (e: Exception) {
                log.error(e) { "Det skjedde en feil ved oppdatering av status på dokument ${it.dokumentreferanse}" }
            }

        }

    }


    @KafkaListener(groupId = "bidrag-dokument-forsendelse", topics = ["\${TOPIC_DOKUMENT}"])
    fun prossesserDokumentHendelse(melding: ConsumerRecord<String, String>) {
        val hendelse = tilDokumentHendelseObjekt(melding)

        if (hendelse.hendelseType == DokumentHendelseType.BESTILLING) return

        log.info { "Mottok hendelse for dokumentreferanse ${hendelse.dokumentreferanse} med status ${hendelse.status}, arkivsystem ${hendelse.arkivSystem} og hendelsetype ${hendelse.hendelseType}" }

        val dokumenter = dokumentTjeneste.hentDokumenterMedReferanse(hendelse.dokumentreferanse)

        val oppdaterteDokumenter = dokumenter.map {
            log.info { "Oppdaterer dokument ${it.dokumentId} med dokumentreferanse ${it.dokumentreferanse} og journalpostid ${it.journalpostId} fra forsendelse ${it.forsendelse.forsendelseId} med informasjon fra hendelse" }
            dokumentTjeneste.lagreDokument(
                it.copy(
                    arkivsystem = if (it.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
                        it.arkivsystem
                    } else {
                        when (hendelse.arkivSystem) {
                            DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                            else -> it.arkivsystem
                        }
                    },
                    dokumentStatus = when (hendelse.status) {
                        DokumentStatusDto.UNDER_REDIGERING -> DokumentStatus.UNDER_REDIGERING
                        DokumentStatusDto.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
                        DokumentStatusDto.AVBRUTT -> DokumentStatus.AVBRUTT
                        else -> it.dokumentStatus
                    },
                    metadata = run {
                        val metadata = it.metadata
                        if (erKvitteringForProdusertDokument(it, hendelse)) {
                            metadata.lagreProdusertTidspunkt(LocalDateTime.now())
                        }
                        metadata.copy()
                    },
                    ferdigstiltTidspunkt = if (hendelse.status == DokumentStatusDto.FERDIGSTILT) LocalDateTime.now() else null,
                    ferdigstiltAvIdent = if (hendelse.status == DokumentStatusDto.FERDIGSTILT) FORSENDELSE_APP_ID else null

                )
            )
        }

        sendJournalposthendelseHvisKlarForDistribusjon(oppdaterteDokumenter)
        ferdigstillHvisForsendelseErNotat(oppdaterteDokumenter)
    }

    private fun erKvitteringForProdusertDokument(dokument: Dokument, dokumentHendelse: DokumentHendelse): Boolean {
        return dokument.dokumentStatus == DokumentStatus.UNDER_PRODUKSJON && (dokumentHendelse.status == DokumentStatusDto.UNDER_REDIGERING || dokumentHendelse.status == DokumentStatusDto.FERDIGSTILT)
    }

    private fun sendJournalposthendelseHvisKlarForDistribusjon(dokumenter: List<Dokument>) {
        dokumenter.forEach {
            if (it.forsendelse.kanDistribueres()) {
                journalpostKafkaHendelseProdusent.publiserForsendelse(it.forsendelse)
            }
        }
    }

    private fun ferdigstillHvisForsendelseErNotat(dokumenter: List<Dokument>) {
        dokumenter.forEach {
            val forsendelse = it.forsendelse

            if (forsendelse.forsendelseType == ForsendelseType.NOTAT && forsendelse.dokumenter.erAlleFerdigstilt) {
                medApplikasjonKontekst {
                    log.info { "Alle dokumenter i forsendelse ${forsendelse.forsendelseId} med type NOTAT er ferdigstilt. Ferdigstiller forsendelse." }
                    ferdigstillForsendelseService.ferdigstillForsendelse(forsendelse.forsendelseId!!)
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
