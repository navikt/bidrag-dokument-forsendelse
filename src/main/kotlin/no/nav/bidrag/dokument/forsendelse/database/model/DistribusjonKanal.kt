package no.nav.bidrag.dokument.forsendelse.database.model

enum class DistribusjonKanal(val beskrivelse: String) {
    NAV_NO("Nav.no"),
    NAV_NO_UINNLOGGET("Nav.no uten ID-porten-pålogging"),
    NAV_NO_CHAT("Innlogget samtale"),
    INNSENDT_NAV_ANSATT("Registrert av Nav-ansatt"),
    LOKAL_UTSKRIFT("Lokal utskrift"),
    SENTRAL_UTSKRIFT("Sentral utskrift"),
    ALTINN("Altinn"),
    EESSI("EESSI"),
    EIA("EIA"),
    EKST_OPPS("Eksternt oppslag"),
    SDP("Digital postkasse til innbyggere"),
    TRYGDERETTEN("Trygderetten"),
    HELSENETTET("Helsenettet"),
    INGEN_DISTRIBUSJON("Ingen distribusjon"),
    UKJENT("Ukjent"),
    DPVT("Taushetsbelagt digital post til virksomhet"),

    SKAN_NETS("Skanning Nets"),
    SKAN_PEN("Skanning Pensjon"),
    SKAN_IM("Skanning Iron Mountain")
}
