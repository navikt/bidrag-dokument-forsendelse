package no.nav.bidrag.dokument.forsendelse.persistence.database.model

import no.nav.bidrag.dokument.forsendelse.model.KLAGE_ANKE_ENHET_KODER
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType

// T_BLANKETT.SOKN_FRA_KODE
enum class SoknadFra(private val kode: String) {
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
    KLAGE_ANKE("FK"); // FTK
}

typealias BehandlingType = String
typealias SoknadType = String

data class DokumentBehandling(
    val tittel: String,
    val detaljer: List<DokumentBehandlingDetaljer>
)

enum class Forvaltning {
    BEGGE,
    KLAGE_ANKE,
    BIDRAG;
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
    IKKE_FATTET;
}

fun BehandlingStatus.isValid(erFattetBeregnet: Boolean? = null): Boolean {
    return if (erFattetBeregnet == null) {
        this == BehandlingStatus.IKKE_RELEVANT || this == BehandlingStatus.IKKE_FATTET
    } else if (this == BehandlingStatus.FATTET_BEREGNET) {
        erFattetBeregnet == true
    } else if (this == BehandlingStatus.FATTET_MANUELT) {
        erFattetBeregnet == false
    } else {
        this == BehandlingStatus.FATTET
    }
}

typealias SoknadTyper = List<SoknadType>

fun DokumentBehandlingDetaljer.isVedtaktypeValid(vt: VedtakType?, st: SoknadType?): Boolean {
    if (st == "EGET_TILTAK") {
        return soknadType.contains(st)
    }
    return vedtakType.contains(vt)
}

data class DokumentBehandlingDetaljer(
    val stonadType: StonadType? = null,
    val engangsbelopType: EngangsbelopType? = null,
    val soknadType: SoknadTyper,
    val vedtakType: List<VedtakType>,
    val soknadFra: List<SoknadFra>,
    val forvaltning: Forvaltning,
    val erVedtakIkkeTilbakekreving: Boolean? = false,
    val behandlingStatus: BehandlingStatus,
    val brevkoder: List<String>
)
