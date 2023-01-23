package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.mapper.tilJournalpostDto
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

val List<Forsendelse>.filtrerIkkeFerdigstiltEllerArkivert get() = this.filter { it.fagarkivJournalpostId == null }

@Component
class ForsendelseInnsynTjeneste(private val forsendelseTjeneste: ForsendelseTjeneste) {

    fun hentForsendelseForSakJournal(saksnummer: String): List<JournalpostDto> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)
        log.info { "Hentet forsendelser for sak $saksnummer" }
        return forsendelser.filtrerIkkeFerdigstiltEllerArkivert
            .map(Forsendelse::tilJournalpostDto)
    }

    fun hentForsendelseJournal(forsendelseId: Long): JournalpostResponse {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        log.info { "Hentet forsendelse for forsendelseId $forsendelseId" }

        return JournalpostResponse(
            journalpost = forsendelse.tilJournalpostDto(),
            sakstilknytninger = listOf(forsendelse.saksnummer)
        )
    }

    fun hentForsendelseForSak(saksnummer: String): List<ForsendelseResponsTo> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.filtrerIkkeFerdigstiltEllerArkivert
            .map(Forsendelse::tilForsendelseRespons)
    }

    fun hentForsendelse(forsendelseId: Long): ForsendelseResponsTo? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null

        return forsendelse.tilForsendelseRespons()
    }


}