package no.nav.bidrag.dokument.forsendelse.consumer.dto

import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler

data class DokumentBestillingForespørsel(
    val mottaker: MottakerTo? = null,
    val saksbehandler: Saksbehandler? = null,
    val gjelderId: String? = null,
    val saksnummer: String,
    val vedtaksId: String? = null,
    val dokumentreferanse: String? = null,
    val tittel: String? = null,
    val enhet: String? = null,
    val språk: String? = null,
)
data class MottakerTo(
    val ident: String? = null,
    val navn: String? = null,
    val språk: String? = null,
    val adresse: MottakerAdresseTo? = null
)

data class MottakerAdresseTo(
    val adresselinje1: String,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val bruksenhetsnummer: String? = null,
    val landkode: String? = null,
    val landkode3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
)

data class DokumentBestillingResponse(
    val dokumentId: String,
    val journalpostId: String,
    val arkivSystem: DokumentArkivSystemDto? = null,
)

data class DokumentMalDetaljer(
    val beskrivelse: String,
    val type: DokumentMalType
)

enum class DokumentMalType {
    UTGÅENDE,
    NOTAT
}