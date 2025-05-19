package no.nav.bidrag.dokument.forsendelse.service.validering

import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterEttersendingsoppgaveRequest
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun OppdaterEttersendingsoppgaveRequest.valider(forsendelse: Forsendelse) {
    val feilliste = mutableListOf<String>()

    if (forsendelse.gjelderIdent != forsendelse.mottaker?.ident) {
        feilliste.add("Kan ikke opprette ettersendingsoppgave hvis gjelder er ulik mottaker")
    }
    if (ettersendelseForJournalpostId.isNotNullOrEmpty() && skjemaId.isNullOrEmpty()) {
        feilliste.add("skjemaId må være satt når ettersendelseForJournalpostId er satt")
    }
    if (feilliste.isNotEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Ugyldig data ved oppdatering av ettersendingsoppgave: ${feilliste.joinToString(", ")}",
        )
    }
}
