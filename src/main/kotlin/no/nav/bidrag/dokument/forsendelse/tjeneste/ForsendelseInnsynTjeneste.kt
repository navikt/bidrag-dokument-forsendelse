package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilJournalpostDto
import org.springframework.stereotype.Component

@Component
class ForsendelseInfoService(val forsendelseRepository: ForsendelseRepository) {


    fun hentForsendelseForSakLegacy(saksnummer: String): List<JournalpostDto> {
        val forsendelser = forsendelseRepository.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.map { forsendelse -> forsendelse.tilJournalpostDto() }
    }

    fun hentForsendelseLegacy(forsendelseId: Long): JournalpostResponse? {
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId) ?: return null

        return JournalpostResponse(
            journalpost = forsendelse.tilJournalpostDto(),
            sakstilknytninger = listOf(forsendelse.saksnummer)
        )
    }

    fun hentForsendelseForSak(saksnummer: String): List<ForsendelseResponsTo> {
        val forsendelser = forsendelseRepository.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.map { forsendelse -> forsendelse.tilForsendelseRespons() }
    }

    fun hentForsendelse(forsendelseId: Long): ForsendelseResponsTo? {
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId) ?: return null

        return forsendelse.tilForsendelseRespons()
    }


}