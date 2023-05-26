package no.nav.bidrag.dokument.forsendelse.persistence.database.model

import no.nav.bidrag.behandling.felles.enums.EngangsbelopType
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.behandling.felles.enums.VedtakType

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
    BARN_18("BB"),
    BIDRAGSENHET("ET"), // TK
    FYLKESNEMDA("FN"),
    NAV_INTERNASJONALT("IN"),
    KOMMUNE("KU"),
    KONVERTERING("KV"),
    BIDRAGSMOTTAKER("MO"),
    NORSKE_MYNDIGH("NM"),
    BIDRAGSPLIKTIG("PL"),
    UTENLANDSKE_MYNDIGH("UM"),
    VERGE("VE"),
    TRYGDEETATEN_INNKREVING("TI"),
    KLAGE_ENHET("FK"); // FTK

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

fun Forvaltning.isEqual(other: Forvaltning? = null): Boolean {
    if (other == null) {
        return this == Forvaltning.BEGGE || this == Forvaltning.BIDRAG
    }
    if (this == Forvaltning.BEGGE) {
        return other == Forvaltning.KLAGE_ANKE || other == Forvaltning.BIDRAG
    }
    return this == other
}

enum class VedtakStatus {
    IKKE_RELEVANT,
    FATTET,
    FATTET_MANUELT,
    FATTET_AUTOMATISK,
    IKKE_FATTET;
}

fun VedtakStatus.isEqual(other: VedtakStatus? = null): Boolean {
    if (other == null) {
        return this == VedtakStatus.IKKE_RELEVANT
    }
    if (this == VedtakStatus.FATTET) {
        return other == VedtakStatus.FATTET_MANUELT || other == VedtakStatus.FATTET_AUTOMATISK
    }
    return this == other
}

data class DokumentBehandlingDetaljer(
    val soknadGruppe: SoknadGruppe,
    val soknadType: List<SoknadType>,

    val stonadType: StonadType? = null,
    val engangsbelopType: EngangsbelopType? = null,
    val vedtakType: List<VedtakType>,
    val soknadFra: List<SoknadFra>,
    val forvaltning: Forvaltning,
    val vedtakStatus: VedtakStatus,
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
    "4295", // Klage og anke Nord
)