package no.nav.bidrag.dokument.forsendelse.api.utvidelser

import no.nav.bidrag.transport.dokument.EndreJournalpostCommand
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterDokumentForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterForsendelseForespørsel
import java.time.LocalDateTime
import java.time.LocalTime

fun EndreJournalpostCommand.tilOppdaterForsendelseForespørsel(): OppdaterForsendelseForespørsel =
    OppdaterForsendelseForespørsel(
        dokumentDato = this.dokumentDato?.let { LocalDateTime.of(it, LocalTime.MIDNIGHT) },
        dokumenter =
            this.endreDokumenter.map {
                OppdaterDokumentForespørsel(
                    dokumentreferanse = it.dokumentreferanse ?: it.dokId,
                    tittel = it.tittel,
                )
            },
    )
