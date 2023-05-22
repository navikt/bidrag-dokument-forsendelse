package no.nav.bidrag.dokument.forsendelse.service

import jakarta.transaction.Transactional
import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DistribuerTilAdresse
import no.nav.bidrag.dokument.dto.DistribusjonInfoDto
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.model.distribusjonFeilet
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.validerKanDistribuere
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Component
class DistribusjonService(
    private val oppdaterForsendelseService: OppdaterForsendelseService,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager
) {

    fun harDistribuert(forsendelse: Forsendelse): Boolean {
        return forsendelse.status == ForsendelseStatus.DISTRIBUERT || forsendelse.status == ForsendelseStatus.DISTRIBUERT_LOKALT
    }

    fun validerKanDistribuere(forsendelseId: Long) {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)

        forsendelse.validerKanDistribuere()
    }

    @Transactional
    fun distribuer(
        forsendelseId: Long,
        distribuerJournalpostRequest: DistribuerJournalpostRequest?,
        batchId: String?
    ): DistribuerJournalpostResponse {
        var forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        if (harDistribuert(forsendelse)) {
            log.info { "Forsendelse $forsendelseId er allerede distribuert med journalpostId ${forsendelse.journalpostIdFagarkiv} og batchId ${forsendelse.batchId}" }
            return DistribuerJournalpostResponse(
                forsendelse.journalpostIdFagarkiv ?: "",
                forsendelse.distribusjonBestillingsId
            )
        }
        validerKanDistribuere(forsendelseId)

        val distribuerLokalt = distribuerJournalpostRequest?.lokalUtskrift ?: false
        log.info { "Bestiller distribusjon av forsendelse $forsendelseId med lokalUtskrift=$distribuerLokalt og batchId=$batchId" }

        if (forsendelse.journalpostIdFagarkiv.isNullOrEmpty()) {
            forsendelse = oppdaterForsendelseService.ferdigstillOgHentForsendelse(forsendelseId, distribuerLokalt)!!
        }

        return if (distribuerLokalt) {
            bestillLokalDistribusjon(forsendelseId, forsendelse, batchId)
        } else {
            bestillDistribusjon(forsendelseId, distribuerJournalpostRequest, forsendelse, batchId)
        }
    }

    private fun bestillLokalDistribusjon(forsendelseId: Long, forsendelse: Forsendelse, batchId: String?): DistribuerJournalpostResponse {
        bidragDokumentConsumer.distribuer("JOARK-${forsendelse.journalpostIdFagarkiv}", lokalUtskrift = true, batchId = batchId)
            ?: distribusjonFeilet(forsendelseId)
        forsendelseTjeneste.lagre(
            forsendelse.copy(
                distribuertAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
                distribuertTidspunkt = LocalDateTime.now(),
                status = ForsendelseStatus.DISTRIBUERT_LOKALT,
                endretAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId()
                    ?: forsendelse.endretAvIdent,
                endretTidspunkt = LocalDateTime.now(),
                distribusjonKanal = DistribusjonKanal.LOKAL_UTSKRIFT
            )
        )
        log.info { "Forsendelsen ble bestilt som distribuert lokalt. Forsendelse og Journalpost markert som distribuert lokalt. Ingen distribusjon er bestilt." }
        return DistribuerJournalpostResponse(
            bestillingsId = null,
            journalpostId = forsendelse.journalpostIdFagarkiv ?: ""
        )
    }

    private fun bestillDistribusjon(
        forsendelseId: Long,
        distribuerJournalpostRequest: DistribuerJournalpostRequest?,
        forsendelse: Forsendelse,
        batchId: String?
    ): DistribuerJournalpostResponse {
        val adresse = distribuerJournalpostRequest?.adresse ?: forsendelse.mottaker?.adresse?.let {
            DistribuerTilAdresse(
                adresselinje1 = it.adresselinje1,
                adresselinje2 = it.adresselinje2,
                adresselinje3 = it.adresselinje3,
                land = it.landkode,
                postnummer = it.postnummer,
                poststed = it.poststed ?: KodeverkService.hentNorskPoststed(it.postnummer, it.landkode)
            )
        }
        val resultat = bidragDokumentConsumer.distribuer("JOARK-${forsendelse.journalpostIdFagarkiv}", adresse, batchId = batchId)
            ?: distribusjonFeilet(forsendelseId)

        log.info("Bestilte distribusjon for forsendelse $forsendelseId med journalpostId=${forsendelse.journalpostIdFagarkiv}, bestillingId=${resultat.bestillingsId} og batchId=$batchId")
        SIKKER_LOGG.info("Bestilte distribusjon for forsendelse $forsendelseId med adresse $adresse, journalpostId=${forsendelse.journalpostIdFagarkiv}, bestillingId=${resultat.bestillingsId} og batchId=$batchId")

        forsendelseTjeneste.lagre(
            forsendelse.copy(
                distribuertAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
                distribuertTidspunkt = LocalDateTime.now(),
                batchId = forsendelse.batchId ?: batchId,
                distribusjonBestillingsId = resultat.bestillingsId,
                status = ForsendelseStatus.DISTRIBUERT,
                endretAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId()
                    ?: forsendelse.endretAvIdent,
                endretTidspunkt = LocalDateTime.now()
            )
        )

        return resultat
    }

    fun hentDistribusjonInfo(journalpostId: String): DistribusjonInfoDto? {
        return bidragDokumentConsumer.hentDistribusjonInfo(journalpostId)
    }
}
