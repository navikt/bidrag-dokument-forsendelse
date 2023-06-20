package no.nav.bidrag.dokument.forsendelse.persistence.database.model

import no.nav.bidrag.behandling.felles.enums.EngangsbelopType
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.model.KLAGE_ANKE_ENHET_KODER

// T_SOKNAD.SOKN_GR_KODE
enum class SoknadGruppe(private val kode: String) {
    AVSKRIVNING("AV"),
    EKTEFELLEBIDRAG("EB"),
    BIDRAG_18_AR("18"),
    BIDRAG("BI"),
    BIDRAG_TILLEGGSBIDRAG("BT"),
    DIREKTE_OPPGJOR("DO"),
    ETTERGIVELSE("EG"),
    ERSTATNING("ER"),
    FARSKAP("FA"),
    FORSKUDD("FO"),
    GEBYR("GB"),
    INNKREVING("IK"),
    MOTREGNING("MR"),
    REFUSJON_BIDRAG("RB"),
    SAKSOMKOSTNINGER("SO"),
    SARTILSKUDD("ST"),
    BIDRAG_18_AR_TILLEGGSBBI("T1"),
    TILLEGGSBIDRAG("TB"),
    TILBAKEKR_ETTERGIVELSE("TE"),
    TILBAKEKREVING("TK"),
    OPPFOSTRINGSBIDRAG("OB"),
    MORSKAP("MO"),
    KUNNSKAP_BIOLOGISK_FAR("FB"),
    BARNEBORTFORING("BF"),
    KONVERTERT_VERDI("KV"),
    REISEKOSTNADER("RK");
}

// T_BLANKETT.SOKN_TYPE
enum class SoknadType(private val kode: String) {
    ENDRING("EN"),
    EGET_TILTAK("ET"),
    SOKNAD("FA"),
    INNKREVINGSGRUNNL("IG"),
    INDEKSREG("IR"),
    KLAGE_BEGR_SATS("KB"),
    KLAGE("KL"),
    FOLGER_KLAGE("KM"),
    KONVERTERING("KV"),
    OMGJORING_BEGR_SATS("OB"),
    OPPJUST_FORSK("OF"),
    OPPHOR("OH"),
    OMGJORING("OM"),
    PRIVAT_AVTALE("PA"),
    BEGR_REVURD("RB"),
    REVURDERING("RF"),
    KORRIGERING("KR");
}

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

data class DokumentBehandlingDetaljer(
    val stonadType: StonadType? = null,
    val engangsbelopType: EngangsbelopType? = null,
    val vedtakType: List<VedtakType>,
    val soknadFra: List<SoknadFra>,
    val forvaltning: Forvaltning,
    val behandlingStatus: BehandlingStatus,
    val brevkoder: List<String>
)

fun SoknadType.tilPrefiks(): String {
    return when (this) {
        SoknadType.SOKNAD -> ""
        else -> ""
    }
}

val ENHET_KLAGE = listOf(
    "4291", // Klage og anke Oslo og Akershus
    "4292", // Klage og anke Midt-Norge
    "4293", // Klage og anke Øst
    "4294", // Klage og anke Vest
    "4295" // Klage og anke Nord
)
