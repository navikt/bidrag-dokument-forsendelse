package no.nav.bidrag.dokument.forsendelse.consumer.dto

import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto

data class DokumentBestillingForespørsel(
    val mottaker: MottakerTo? = null,
    val saksbehandler: Saksbehandler? = null,
    val gjelderId: String? = null,
    val saksnummer: String,
    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val dokumentreferanse: String? = null,
    val tittel: String? = null,
    val enhet: String? = null,
    val språk: String? = null,
    val barnIBehandling: List<String> = emptyList(),
)

data class MottakerTo(
    val ident: String? = null,
    val navn: String? = null,
    val språk: String? = null,
    val adresse: MottakerAdresseTo? = null,
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
    val tittel: String,
    val type: DokumentMalType,
    val kanBestilles: Boolean = false,
    val redigerbar: Boolean = false,
    val beskrivelse: String = tittel,
    val statiskInnhold: Boolean = false,
    val kreverVedtak: Boolean = false,
    val kreverBehandling: Boolean = false,
    val innholdType: DokumentmalInnholdType? = null,
    val gruppeVisningsnavn: String? = null,
    val språk: List<String> = emptyList(),
    val tilhorerEnheter: List<String> = emptyList(),
    val alternativeTitler: List<String> = emptyList(),
)

enum class DokumentmalInnholdType {
    NOTAT,
    VARSEL_STANDARD,
    VARSEL,
    VEDTAK,
    VEDLEGG_VEDTAK,
    VEDLEGG_VARSEL,
    VEDLEGG,
    SKJEMA,
}

enum class DokumentMalType {
    UTGÅENDE,
    NOTAT,
}
