package no.nav.bidrag.dokument.forsendelse.api.utvidelser

import no.nav.bidrag.dokument.dto.EndreJournalpostCommand
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty

internal val EndreJournalpostCommand.harMottaker get(): Boolean = this.avsenderMottakerId.isNotNullOrEmpty() || this.avsenderMottakerNavn.isNotNullOrEmpty()
fun EndreJournalpostCommand.tilOppdaterForsendelseForespørsel(): OppdaterForsendelseForespørsel{

    return OppdaterForsendelseForespørsel(
        gjelderIdent = this.gjelder,
        saksnummer = this.tilknyttSaker.first(),
        mottaker = MottakerTo(this.avsenderMottakerId, this.avsenderMottakerNavn).takeIf { this.harMottaker },
        dokumenter = this.endreDokumenter.map {
            OppdaterDokumentForespørsel(
                dokumentreferanse = it.dokumentreferanse ?: it.dokId,
                tittel = it.tittel,
                dokumentmalId = it.brevkode
            )
        }
    )
}