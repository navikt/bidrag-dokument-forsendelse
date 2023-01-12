package no.nav.bidrag.dokument.forsendelse.api.utvidelser

import no.nav.bidrag.dokument.dto.EndreJournalpostCommand
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel

fun EndreJournalpostCommand.tilOppdaterForsendelseForespørsel(): OppdaterForsendelseForespørsel{

    return OppdaterForsendelseForespørsel(
        dokumenter = this.endreDokumenter.map {
            OppdaterDokumentForespørsel(
                dokumentreferanse = it.dokumentreferanse ?: it.dokId,
                tittel = it.tittel
            )
        }
    )
}