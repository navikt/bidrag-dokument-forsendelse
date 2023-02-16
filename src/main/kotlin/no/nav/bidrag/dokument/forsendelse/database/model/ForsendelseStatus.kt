package no.nav.bidrag.dokument.forsendelse.database.model

enum class ForsendelseStatus {
    UNDER_PRODUKSJON,
    FERDIGSTILT,
    AVBRUTT,
    SLETTET,
    DISTRIBUERT,
    DISTRIBUERT_LOKALT
}