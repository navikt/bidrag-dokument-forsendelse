package no.nav.bidrag.dokument.forsendelse.api.dto

import jakarta.validation.constraints.NotBlank

data class FerdigstillDokumentRequest(
    @field:NotBlank(message = "Fysisk dokument kan ikke være tom")
    val fysiskDokument: ByteArray,
    val redigeringMetadata: String? = null
)
