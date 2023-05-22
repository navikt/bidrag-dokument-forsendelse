package no.nav.bidrag.dokument.forsendelse.persistence.database.model

// T_SOKNAD.SOKN_GR_KODE
enum class BehandlingType(private val kode: String) {
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
    KV("KV"),
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
    KR("KR");
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
    TI("TI"), //TRYGDEETATEN_INNKREVING
    KLAGE_ENHET("FK"); // FTK

}

data class DokumentBehandling(
    val tittel: String,
    val detaljer: List<DokumentBehandlingDetaljer>
)

data class DokumentBehandlingDetaljer(
    val behandlingType: List<BehandlingType>,
    val soknadType: SoknadType,
    val klage: Boolean?,
    val fattetVedtak: Boolean?,
    val manuelBeregning: Boolean?,
    val soknadFra: List<SoknadFra>,
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