package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.mapper.tilJournalpostDto
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

val List<Forsendelse>.filtrerIkkeFerdigstiltEllerArkivert
    get() = this.filter { it.journalpostIdFagarkiv == null }.filter { it.status != ForsendelseStatus.SLETTET }

@Component
class ForsendelseInnsynTjeneste(private val forsendelseTjeneste: ForsendelseTjeneste, private val tilgangskontrollService: TilgangskontrollService) {

    fun hentForsendelseForSakJournal(saksnummer: String, temaListe: List<JournalTema> = listOf(JournalTema.BID)): List<JournalpostDto> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)
        val forsendelserFiltrert = forsendelser.filtrerIkkeFerdigstiltEllerArkivert
            .filter { temaListe.map { jt -> jt.name }.contains(it.tema.name) }
            .filter { tilgangskontrollService.harTilgangTilTema(it.tema.name) }
            .map(Forsendelse::tilJournalpostDto)

        log.info { "Hentet ${forsendelserFiltrert.size} forsendelser for sak $saksnummer og temaer $temaListe" }
        return forsendelserFiltrert
    }

    fun hentForsendelseJournal(forsendelseId: Long): JournalpostResponse {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        log.info { "Hentet forsendelse $forsendelseId med saksnummer ${forsendelse.saksnummer}" }

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