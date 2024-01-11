package no.nav.bidrag.dokument.forsendelse.persistence.database.model

enum class DokumentArkivSystem {
    JOARK,
    MIDLERTIDLIG_BREVLAGER,
    UKJENT,
    BIDRAG,
    FORSENDELSE, // Dokumentet er en symlink til et dokument i en annen forsendelse
}
