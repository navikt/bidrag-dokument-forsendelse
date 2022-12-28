package no.nav.bidrag.dokument.forsendelse.database.model

enum class DokumentStatus {
    UNDER_PRODUKSJON, // Dokumentet
    UNDER_REDIGERING, // Dokumentet er under arbeid. Benyttes for redigerbare brev.
    FERDIGSTILT, // Dokumentet er ferdigstilt.
    IKKE_BESTILT, // Ingen bestilling har blitt sendt for produksjon av dokumentet.
    BESTILT, // Bestilling har blitt sendt for produksjon av dokumentet.
    AVBRUTT // Dokumentet ble opprettet, men ble avbrutt under redigering. Benyttes for redigerbare brev.
}