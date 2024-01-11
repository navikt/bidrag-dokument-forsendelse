package no.nav.bidrag.dokument.forsendelse.api.dto

import io.swagger.v3.oas.annotations.media.Schema

data class DokumentRedigeringMetadataResponsDto(
    val tittel: String,
    @Schema(enumAsRef = true) val status: DokumentStatusTo,
    @Schema(enumAsRef = true) val forsendelseStatus: ForsendelseStatusTo,
    val redigeringMetadata: String?,
    val dokumenter: List<DokumentDetaljer> = emptyList(),
)

data class DokumentDetaljer(
    val tittel: String,
    val dokumentreferanse: String?,
    val antallSider: Int = 0,
)
