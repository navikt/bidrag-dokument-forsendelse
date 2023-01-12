package no.nav.bidrag.dokument.forsendelse.tjeneste

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DistribuerTilAdresse
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragDokumentKonsumer
import no.nav.bidrag.dokument.forsendelse.model.distribusjonFeilet
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.kanIkkeDistribuereForsendelse
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import javax.transaction.Transactional
private val log = KotlinLogging.logger {}

@Component
class DistribusjonTjeneste(
    private val oppdaterForsendelseTjeneste: OppdaterForsendelseTjeneste,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val bidragDokumentKonsumer: BidragDokumentKonsumer,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager
) {

    fun kanDistribuere(forsendelseId: Long): Boolean {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return false

        if (forsendelse.forsendelseType != ForsendelseType.UTGÅENDE) return false

        return forsendelse.status == ForsendelseStatus.FERDIGSTILT
                || forsendelse.dokumenter.erAlleFerdigstilt && forsendelse.status == ForsendelseStatus.UNDER_PRODUKSJON
    }

    @Transactional
    fun distribuer(forsendelseId: Long, distribuerJournalpostRequest: DistribuerJournalpostRequest?): DistribuerJournalpostResponse {
        if (!kanDistribuere(forsendelseId)) kanIkkeDistribuereForsendelse(forsendelseId)

        log.info { "Bestiller distribusjon for forsendelse $forsendelseId" }
        var forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)

        if (forsendelse.fagarkivJournalpostId.isNullOrEmpty()){
            forsendelse = oppdaterForsendelseTjeneste.ferdigstillOgHentForsendelse(forsendelseId)!!
        }

        val adresse = distribuerJournalpostRequest?.adresse ?: forsendelse.mottaker?.adresse?.let {
            DistribuerTilAdresse(
                adresselinje1 = it.adresselinje1,
                adresselinje2 = it.adresselinje2,
                adresselinje3 = it.adresselinje3,
                land = it.landkode,
                postnummer = it.postnummer,
                poststed = it.poststed
            )
        }

        val resultat = bidragDokumentKonsumer.distribuer("JOARK-${forsendelse.fagarkivJournalpostId}", adresse) ?: distribusjonFeilet(forsendelseId)

        log.info("Bestilte distribusjon for forsendelse $forsendelseId med journalpostId=${forsendelse.fagarkivJournalpostId} og bestillingId=${resultat.bestillingsId}")
        SIKKER_LOGG.info("Bestilte distribusjon for forsendelse $forsendelseId med adresse $adresse, journalpostId=${forsendelse.fagarkivJournalpostId} og bestillingId=${resultat.bestillingsId}")

        forsendelseTjeneste.lagre(forsendelse.copy(
            distribuertAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
            distribuertTidspunkt = LocalDateTime.now(),
            distribusjonBestillingsId = resultat.bestillingsId,
            status = ForsendelseStatus.DISTRIBUERT,
            endretAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId() ?: forsendelse.endretAvIdent,
            endretTidspunkt = LocalDateTime.now()
        ))

        return resultat

    }
}