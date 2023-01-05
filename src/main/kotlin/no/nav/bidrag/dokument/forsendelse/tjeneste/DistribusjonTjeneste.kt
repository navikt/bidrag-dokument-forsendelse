package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DistribuerTilAdresse
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragDokumentKonsumer
import no.nav.bidrag.dokument.forsendelse.model.KanIkkeDistribuereForsendelse
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.erAlleFerdigstilt
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import javax.transaction.Transactional

@Component
class DistribusjonTjeneste(
    private val oppdaterForsendelseTjeneste: OppdaterForsendelseTjeneste,
    private val forsendelseTjeneste: ForsendelseTjeneste, private val bidragDokumentKonsumer: BidragDokumentKonsumer, private val saksbehandlerInfoManager: SaksbehandlerInfoManager) {

    fun kanDistribuere(forsendelseId: Long): Boolean {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return false

        return forsendelse.status == ForsendelseStatus.FERDIGSTILT
                || forsendelse.dokumenter.erAlleFerdigstilt && forsendelse.status == ForsendelseStatus.UNDER_PRODUKSJON
    }

    @Transactional
    fun distribuer(forsendelseId: Long, distribuerJournalpostRequest: DistribuerJournalpostRequest?): DistribuerJournalpostResponse? {
        var forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null

        if (forsendelse.arkivJournalpostId.isNullOrEmpty()){
            oppdaterForsendelseTjeneste.ferdigstillForsendelse(forsendelseId)
            forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)!!
        }

        if (forsendelse.arkivJournalpostId.isNullOrEmpty()){
            throw KanIkkeDistribuereForsendelse(forsendelseId)
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

        val resultat = bidragDokumentKonsumer.distribuer("JOARK-${forsendelse.arkivJournalpostId}", adresse) ?: return null

        forsendelseTjeneste.lagre(forsendelse.copy(
            distribuertAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
            distribuertTidspunkt = LocalDateTime.now(),
            distribusjonBestillingsId = resultat.bestillingsId,
            status = ForsendelseStatus.DISTRIBUERT
        ))

        return resultat

    }
}