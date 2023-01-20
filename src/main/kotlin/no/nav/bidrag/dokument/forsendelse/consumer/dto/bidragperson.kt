package no.nav.bidrag.dokument.forsendelse.consumer.dto

data class HentPersonResponse(
    val ident: String,
    val navn: String,
    val aktoerId: String
)