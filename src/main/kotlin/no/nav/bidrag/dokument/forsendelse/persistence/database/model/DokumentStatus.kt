package no.nav.bidrag.dokument.forsendelse.persistence.database.model

enum class DokumentStatus {
    UNDER_PRODUKSJON, // Dokumentet er bestilt og venter kvittering for at dokumentet er produsert.
    UNDER_REDIGERING, // Dokumentet er under arbeid. Benyttes for redigerbare brev.
    FERDIGSTILT, // Dokumentet er ferdigstilt.
    IKKE_BESTILT, // Ingen bestilling har blitt sendt for produksjon av dokumentet.
    BESTILLING_FEILET,
    AVBRUTT, // Dokumentet ble opprettet, men ble avbrutt under redigering. Benyttes for redigerbare brev.
    MÅ_KONTROLLERES, // Dokumentet er importert fra annen kilde og må kontrolleres
    KONTROLLERT,
}
