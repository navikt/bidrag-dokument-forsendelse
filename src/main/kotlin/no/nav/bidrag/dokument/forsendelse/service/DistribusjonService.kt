package no.nav.bidrag.dokument.forsendelse.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.model.distribusjonFeilet
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilDto
import no.nav.bidrag.dokument.forsendelse.utvidelser.validerKanDistribuere
import no.nav.bidrag.transport.dokument.DistribuerJournalpostRequest
import no.nav.bidrag.transport.dokument.DistribuerJournalpostResponse
import no.nav.bidrag.transport.dokument.DistribuerTilAdresse
import no.nav.bidrag.transport.dokument.DistribusjonInfoDto
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Component
class DistribusjonService(
    private val ferdigstillForsendelseService: FerdigstillForsendelseService,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val dokumentStorageService: DokumentStorageService,
    private val hendelseBestillingService: ForsendelseHendelseBestillingService,
    private val meterRegistry: MeterRegistry,
) {
    fun harDistribuert(forsendelse: Forsendelse): Boolean =
        forsendelse.status == ForsendelseStatus.DISTRIBUERT || forsendelse.status == ForsendelseStatus.DISTRIBUERT_LOKALT

    fun størrelseIMb(forsendelseId: Long): Long {
        val fileSize = bytesToMb(dokumentStorageService.totalStørrelse(forsendelseId))
        log.info { "Forsendelse $forsendelseId har dokumenter på total størrelse $fileSize Mb" }
        return fileSize.toLong()
    }

    fun validerKanDistribuere(forsendelseId: Long) {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)

        forsendelse.validerKanDistribuere()
    }

    @Transactional
    fun distribuer(
        forsendelseId: Long,
        distribuerJournalpostRequest: DistribuerJournalpostRequest?,
        batchId: String?,
        ingenDistribusjon: Boolean,
    ): DistribuerJournalpostResponse {
        val distribuerLokalt = distribuerJournalpostRequest?.lokalUtskrift ?: false
        log.info {
            "Bestiller distribusjon av forsendelse $forsendelseId med lokalUtskrift=$distribuerLokalt, " +
                "ingenDistribusjon=$ingenDistribusjon og batchId=$batchId"
        }

        var forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)

        if (harDistribuert(forsendelse)) {
            log.info {
                "Forsendelse $forsendelseId er allerede distribuert med journalpostId " +
                    "${forsendelse.journalpostIdFagarkiv} og batchId ${forsendelse.batchId}"
            }
            return DistribuerJournalpostResponse(
                forsendelse.journalpostIdFagarkiv ?: "",
                forsendelse.distribusjonBestillingsId,
            )
        }
        validerKanDistribuere(forsendelseId)

        if (forsendelse.journalpostIdFagarkiv.isNullOrEmpty()) {
            forsendelse = ferdigstillForsendelseService.ferdigstillOgHentForsendelse(forsendelseId, distribuerLokalt, ingenDistribusjon)!!
        }

        val result =
            if (ingenDistribusjon) {
                markerSomIngenDistribusjon(forsendelse)
            } else if (distribuerLokalt) {
                bestillLokalDistribusjon(forsendelseId, forsendelse, batchId)
            } else {
                bestillDistribusjon(forsendelseId, distribuerJournalpostRequest, forsendelse, batchId)
            }

        hendelseBestillingService.bestill(forsendelseId)
        log.info {
            "Har bestilt distribusjon av forsendelse $forsendelseId med lokalUtskrift=$distribuerLokalt, " +
                "ingenDistribusjon=$ingenDistribusjon og batchId=$batchId og sendt ut hendelse"
        }

        return result
    }

    private fun markerSomIngenDistribusjon(forsendelse: Forsendelse): DistribuerJournalpostResponse {
        measureIngenDistribusjon(forsendelse)
        forsendelseTjeneste.lagre(
            forsendelse.copy(
                distribuertAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
                distribuertTidspunkt = LocalDateTime.now(),
                status = ForsendelseStatus.DISTRIBUERT,
                endretAvIdent =
                    saksbehandlerInfoManager.hentSaksbehandlerBrukerId()
                        ?: forsendelse.endretAvIdent,
                endretTidspunkt = LocalDateTime.now(),
                distribusjonKanal = DistribusjonKanal.INGEN_DISTRIBUSJON,
            ),
        )
        log.info {
            "Forsendelsen ferdigstilt uten distribusjon. " +
                "Forsendelse og Journalpost markert som ikke distribuert med kanal INGEN_DISTRIBUSJON."
        }
        return DistribuerJournalpostResponse(
            bestillingsId = null,
            journalpostId = forsendelse.journalpostIdFagarkiv ?: "",
        )
    }

    private fun bestillLokalDistribusjon(
        forsendelseId: Long,
        forsendelse: Forsendelse,
        batchId: String?,
    ): DistribuerJournalpostResponse {
        bidragDokumentConsumer.distribuer("JOARK-${forsendelse.journalpostIdFagarkiv}", lokalUtskrift = true, batchId = batchId)
            ?: distribusjonFeilet(forsendelseId)
        forsendelseTjeneste.lagre(
            forsendelse.copy(
                distribuertAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
                distribuertTidspunkt = LocalDateTime.now(),
                status = ForsendelseStatus.DISTRIBUERT_LOKALT,
                endretAvIdent =
                    saksbehandlerInfoManager.hentSaksbehandlerBrukerId()
                        ?: forsendelse.endretAvIdent,
                endretTidspunkt = LocalDateTime.now(),
                distribusjonKanal = DistribusjonKanal.LOKAL_UTSKRIFT,
            ),
        )
        log.info {
            "Forsendelsen ble bestilt som distribuert lokalt. " +
                "Forsendelse og Journalpost markert som distribuert lokalt. Ingen distribusjon er bestilt."
        }
        return DistribuerJournalpostResponse(
            bestillingsId = null,
            journalpostId = forsendelse.journalpostIdFagarkiv ?: "",
        )
    }

    private fun bestillDistribusjon(
        forsendelseId: Long,
        distribuerJournalpostRequest: DistribuerJournalpostRequest?,
        forsendelse: Forsendelse,
        batchId: String?,
    ): DistribuerJournalpostResponse {
        val adresse =
            distribuerJournalpostRequest?.adresse ?: forsendelse.mottaker?.adresse?.let {
                DistribuerTilAdresse(
                    adresselinje1 = it.adresselinje1,
                    adresselinje2 = it.adresselinje2,
                    adresselinje3 = it.adresselinje3,
                    land = it.landkode,
                    postnummer = it.postnummer,
                    poststed = it.poststed ?: hentNorskPoststed(it.postnummer, it.landkode),
                )
            }
        val ettersendingsoppgave =
            forsendelse.ettersendingsoppgave?.tilDto()
        val resultat =
            bidragDokumentConsumer.distribuer(
                "JOARK-${forsendelse.journalpostIdFagarkiv}",
                adresse,
                batchId = batchId,
                ettersendingsoppgave = ettersendingsoppgave,
            )
                ?: distribusjonFeilet(forsendelseId)

        log.info {
            "Bestilte distribusjon for forsendelse $forsendelseId med journalpostId=${forsendelse.journalpostIdFagarkiv}, " +
                "bestillingId=${resultat.bestillingsId} og batchId=$batchId"
        }
        SIKKER_LOGG.info {
            "Bestilte distribusjon for forsendelse $forsendelseId med adresse $adresse, " +
                "journalpostId=${forsendelse.journalpostIdFagarkiv}, bestillingId=${resultat.bestillingsId} og batchId=$batchId"
        }

        forsendelseTjeneste.lagre(
            forsendelse.copy(
                distribuertAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
                distribuertTidspunkt = LocalDateTime.now(),
                batchId = forsendelse.batchId ?: batchId,
                distribusjonBestillingsId = resultat.bestillingsId,
                status = ForsendelseStatus.DISTRIBUERT,
                endretAvIdent =
                    saksbehandlerInfoManager.hentSaksbehandlerBrukerId()
                        ?: forsendelse.endretAvIdent,
                endretTidspunkt = LocalDateTime.now(),
                ettersendingsoppgave =
                    forsendelse.ettersendingsoppgave?.let {
                        it.innsendingsId = resultat.ettersendingsoppgave?.innsendingsId
                        it
                    },
            ),
        )

        forsendelse.dokumenter
            .filter { it.dokumentStatus == DokumentStatus.KONTROLLERT }
            .forEach {
                dokumentStorageService.bestillSletting(forsendelseId, it.dokumentreferanse)
            }

        return resultat
    }

    private fun measureIngenDistribusjon(forsendelse: Forsendelse) {
        meterRegistry
            .counter(
                "forsendelse_ingen_distribusjon",
                "enhet",
                forsendelse.enhet,
                "tema",
                forsendelse.tema.name,
            ).increment()
    }

    fun hentDistribusjonInfo(journalpostId: String): DistribusjonInfoDto? = bidragDokumentConsumer.hentDistribusjonInfo(journalpostId)

    fun bytesToMb(bytes: Long): Float {
        val kilobyte = 1024F
        val megabyte = kilobyte * 1024F
        return bytes / megabyte
    }
}
