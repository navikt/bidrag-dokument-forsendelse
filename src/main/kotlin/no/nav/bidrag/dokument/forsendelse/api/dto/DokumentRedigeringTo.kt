package no.nav.bidrag.dokument.forsendelse.api.dto

data class DokumentRedigeringMetadataResponsDto(
    val tittel: String,
    val status: DokumentStatusTo,
    val forsendelseStatus: ForsendelseStatusTo,
    val redigeringMetadata: String?,
    val dokumenter: List<DokumentDetaljer> = emptyList()
)

data class DokumentDetaljer(
    val tittel: String,
    val dokumentreferanse: String?,
    val antallSider: Int = 0
)
