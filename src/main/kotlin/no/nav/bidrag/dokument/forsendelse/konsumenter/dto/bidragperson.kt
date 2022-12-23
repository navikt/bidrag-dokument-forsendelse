package no.nav.bidrag.dokument.forsendelse.konsumenter.dto

data class HentPersonResponse(
    val ident: String,
    val navn: String,
    val aktoerId: String
)