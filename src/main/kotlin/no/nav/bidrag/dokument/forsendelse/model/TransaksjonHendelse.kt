package no.nav.bidrag.dokument.forsendelse.model

data class DokumentBestilling(
    val forsendelseId: Long,
    val dokumentreferanse: String,
    val waitForCommit: Boolean = true,
)

data class ForsendelseHendelseBestilling(
    val forsendelseId: Long,
)

data class DokumentBestillSletting(
    val forsendelseId: Long,
    val dokumentreferanse: String,
)
