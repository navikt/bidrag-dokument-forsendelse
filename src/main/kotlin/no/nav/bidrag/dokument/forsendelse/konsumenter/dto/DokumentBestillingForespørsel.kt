package no.nav.bidrag.dokument.forsendelse.konsumenter.dto

data class DokumentBestillingForespørsel(
    val mottakerId: String? = null,
    val mottaker: MottakerTo? = null,
    val samhandlerInformasjon: SamhandlerInformasjon? = null,
    val saksbehandler: Saksbehandler? = null,
    val gjelderId: String? = null,
    val saksnummer: String,
    val vedtaksId: String? = null,
    val dokumentreferanse: String? = null,
    val tittel: String? = null,
    val enhet: String? = null,
    val språk: String? = null,
)
data class Saksbehandler(
    val ident: String? = null,
    val navn: String? = null
)
data class MottakerTo(
    val ident: String? = null,
    val navn: String? = null,
    val språk: String? = null,
    val adresse: MottakerAdresseTo? = null
)

data class MottakerAdresseTo(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String? = null,
    val landkode: String? = null,
)

data class DokumentBestillingResponse(
    val dokumentId: String,
    val journalpostId: String,
    val arkivSystem: DokumentArkivSystemTo? = null,
)

enum class DokumentArkivSystemTo {
    MIDLERTIDLIG_BREVLAGER
}

data class SamhandlerAdresse(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String? = null,
    val landkode: String? = null,
)

data class SamhandlerInformasjon(
    val navn: String? = null,
    val spraak: String? = null,
    val adresse: SamhandlerAdresse? = null
)