package no.nav.bidrag.dokument.forsendelse.consumer.dto

import java.time.LocalDate
data class HentPersonInfoRequest(
        var ident: String
)
data class HentPersonResponse(
        val ident: String,
        val navn: String,
        val kortNavn: String? = null,
        val foedselsdato: LocalDate? = null,
        val doedsdato: LocalDate? = null,
        val aktoerId: String? = null,
        val diskresjonskode: String? = null,
){

    val fornavnEtternavn get () = run {
        val navnSplit = navn.split(",")
        val fornavnMellomnavn = if (navnSplit.size == 2) navnSplit[1] else navnSplit[0]
        val etternavn = if (navnSplit.size == 2) navnSplit[0] else ""
        "$fornavnMellomnavn $etternavn"
    }

    val fornavn get() = run {
        val navnSplit = navn.split(",")
        val fornavnMellomnavn = if (navnSplit.size == 2) navnSplit[1] else navnSplit[0]
        fornavnMellomnavn.trim().split(" ")[0]
    }
}