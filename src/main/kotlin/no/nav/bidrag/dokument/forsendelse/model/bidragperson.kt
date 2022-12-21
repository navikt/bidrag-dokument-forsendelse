package no.nav.bidrag.dokument.forsendelse.model

data class HentPersonResponse(
    val ident: String,
    val navn: String,
    val aktoerId: String
)