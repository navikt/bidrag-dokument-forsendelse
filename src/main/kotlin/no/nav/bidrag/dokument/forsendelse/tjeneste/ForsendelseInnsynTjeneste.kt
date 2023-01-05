package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.model.Dokumentreferanse
import no.nav.bidrag.dokument.forsendelse.model.FantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hent
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilJournalpostDto
import org.springframework.stereotype.Component

@Component
class ForsendelseInnsynTjeneste(val forsendelseTjeneste: ForsendelseTjeneste) {

    fun hentForsendelseForSakLegacy(saksnummer: String): List<JournalpostDto> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.filter { it.arkivJournalpostId == null }.map { forsendelse -> forsendelse.tilJournalpostDto() }
    }

    fun hentForsendelseLegacy(forsendelseId: Long): JournalpostResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null

        return JournalpostResponse(
            journalpost = forsendelse.tilJournalpostDto(),
            sakstilknytninger = listOf(forsendelse.saksnummer)
        )
    }

    fun hentForsendelseForSak(saksnummer: String): List<ForsendelseResponsTo> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.map { forsendelse -> forsendelse.tilForsendelseRespons() }
    }

    fun hentForsendelse(forsendelseId: Long): ForsendelseResponsTo? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null

        return forsendelse.tilForsendelseRespons()
    }


}