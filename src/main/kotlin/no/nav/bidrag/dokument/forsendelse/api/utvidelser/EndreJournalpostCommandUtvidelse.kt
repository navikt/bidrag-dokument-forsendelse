package no.nav.bidrag.dokument.forsendelse.api.utvidelser

import no.nav.bidrag.dokument.dto.EndreJournalpostCommand
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import java.time.LocalDateTime
import java.time.LocalTime

fun EndreJournalpostCommand.tilOppdaterForsendelseForespørsel(): OppdaterForsendelseForespørsel {

    return OppdaterForsendelseForespørsel(
        dokumentDato = this.dokumentDato?.let { LocalDateTime.of(it, LocalTime.MIDNIGHT) },
        dokumenter = this.endreDokumenter.map {
            OppdaterDokumentForespørsel(
                dokumentreferanse = it.dokumentreferanse ?: it.dokId,
                tittel = it.tittel
            )
        }
    )
}