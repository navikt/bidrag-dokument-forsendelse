package no.nav.bidrag.dokument.forsendelse.service.validering

import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterEttersendingsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun OppdaterEttersendingsoppgaveRequest.valider() {
    val feilliste = mutableListOf<String>()

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
