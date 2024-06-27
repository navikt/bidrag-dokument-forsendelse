package no.nav.bidrag.dokument.forsendelse.persistence.database.model

import no.nav.bidrag.dokument.forsendelse.model.KLAGE_ANKE_ENHET_KODER
import no.nav.bidrag.dokument.forsendelse.model.konverterFraDeprekerteVerdier
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype

// T_BLANKETT.SOKN_FRA_KODE
enum class SoknadFra(
    private val kode: String,
) {
    BM_I_ANNEN_SAK("AS"),
    BARN_18_AAR("BB"),
    NAV_BIDRAG("ET"), // TK
    FYLKESNEMDA("FN"),
    NAV_INTERNASJONALT("IN"),
    KOMMUNE("KU"),
    KONVERTERING("KV"), // Trenger vi dette?
    BIDRAGSMOTTAKER("MO"),
    NORSKE_MYNDIGHET("NM"),
    BIDRAGSPLIKTIG("PL"),
    UTENLANDSKE_MYNDIGHET("UM"),
    VERGE("VE"),
    TRYGDEETATEN_INNKREVING("TI"),
    KLAGE_ANKE("FK"), // FTK
}

typealias BehandlingType = String
typealias SoknadType = String

data class DokumentBehandling(
    val tittel: String,
    val detaljer: List<DokumentBehandlingDetaljer>,
)

enum class Forvaltning {
    BEGGE,
    KLAGE_ANKE,
    BIDRAG,
}

fun Forvaltning.isValid(enhet: String? = null): Boolean {
    if (enhet == null) {
        return this == Forvaltning.BEGGE || this == Forvaltning.BIDRAG
    }
    if (this == Forvaltning.KLAGE_ANKE) {
        return KLAGE_ANKE_ENHET_KODER.contains(enhet)
    }
    return true
}

enum class BehandlingStatus {
    IKKE_RELEVANT,
    FATTET,
    FATTET_MANUELT,
    FATTET_BEREGNET,
    IKKE_FATTET,
}

fun BehandlingStatus.isValid(erFattetBeregnet: Boolean? = null): Boolean =
    if (erFattetBeregnet == null) {
        this == BehandlingStatus.IKKE_RELEVANT || this == BehandlingStatus.IKKE_FATTET
    } else if (this == BehandlingStatus.FATTET_BEREGNET) {
        erFattetBeregnet == true
    } else if (this == BehandlingStatus.FATTET_MANUELT) {
        erFattetBeregnet == false
    } else {
        this == BehandlingStatus.FATTET
    }

typealias SoknadTyper = List<SoknadType>

fun DokumentBehandlingDetaljer.isVedtaktypeValid(
    vt: Vedtakstype?,
    st: SoknadType?,
): Boolean {
    if (st == "EGET_TILTAK" || st == "OMGJORING" || st == "BEGRENSET_REVURDERING" || st == "KLAGE") {
        return soknadType.contains(st)
    }
    return vedtakType.contains(vt)
}

fun DokumentBehandlingTittelDetaljer.isVedtaktypeValid(
    vt: Vedtakstype?,
    st: SoknadType?,
): Boolean {
    if (st == "EGET_TILTAK" || st == "OMGJORING" || st == "BEGRENSET_REVURDERING" || st == "PRIVAT_AVTALE") {
        return soknadType.contains(st)
    }
    return vedtakType.contains(vt)
}

fun DokumentBehandlingTittelDetaljer.erVedtakTilbakekrevingLik(erVedtakIkkeTilbakekreving: Boolean?): Boolean {
    if (erVedtakIkkeTilbakekreving == null) return this.erVedtakIkkeTilbakekreving == false
    return this.erVedtakIkkeTilbakekreving == erVedtakIkkeTilbakekreving
}

data class DokumentBehandlingDetaljer(
    val stonadType: Stønadstype? = null,
    val engangsbelopType: Engangsbeløptype? = null,
    val soknadType: SoknadTyper,
    val vedtakType: List<Vedtakstype>,
    val soknadFra: List<SøktAvType>,
    val forvaltning: Forvaltning,
    val erVedtakIkkeTilbakekreving: Boolean = false,
    val behandlingStatus: BehandlingStatus,
    val brevkoder: List<String>,
)

data class DokumentBehandlingTittelDetaljer(
    val behandlingType: BehandlingType? = null,
    val stonadType: Stønadstype? = null,
    val engangsbelopType: Engangsbeløptype? = null,
    val soknadType: SoknadTyper = emptyList(),
    val vedtakType: List<Vedtakstype> = emptyList(),
    val soknadFra: List<SøktAvType> = emptyList(),
    val forvaltning: Forvaltning? = null,
    val erVedtakIkkeTilbakekreving: Boolean? = false,
    val behandlingStatus: BehandlingStatus,
    val titler: List<String>,
) {
    val engangsbeløptypeKonvertert
        get() = engangsbelopType?.konverterFraDeprekerteVerdier()
}
