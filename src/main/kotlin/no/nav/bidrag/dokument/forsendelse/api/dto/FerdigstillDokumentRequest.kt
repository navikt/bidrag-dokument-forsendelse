package no.nav.bidrag.dokument.forsendelse.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class FerdigstillDokumentRequest(
    @field:NotBlank(message = "Fysisk dokument kan ikke være tom")
    @Schema(type = "string", format = "binary")
    val fysiskDokument: ByteArray,
    val redigeringMetadata: String? = null,
)
