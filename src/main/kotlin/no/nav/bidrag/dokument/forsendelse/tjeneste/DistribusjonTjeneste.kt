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

@Component
class DistribusjonTjeneste(private val forsendelseTjeneste: ForsendelseTjeneste, private val bidragDokumentKonsumer: BidragDokumentKonsumer, private val saksbehandlerInfoManager: SaksbehandlerInfoManager) {

    fun kanDistribuere(forsendelseId: Long): Boolean {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return false

        return forsendelse.dokumenter.erAlleFerdigstilt && forsendelse.status == ForsendelseStatus.FERDIGSTILT
    }

    fun distribuer(forsendelseId: Long, distribuerJournalpostRequest: DistribuerJournalpostRequest?): DistribuerJournalpostResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null

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

        val resultat = bidragDokumentKonsumer.distribuer(forsendelse.arkivJournalpostId, adresse) ?: return null

        forsendelseTjeneste.lagre(forsendelse.copy(
            distribuertAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
            distribuertTidspunkt = LocalDateTime.now(),
            distribusjonBestillingsId = resultat.bestillingsId,
        ))

        return resultat

    }
}