package no.nav.bidrag.dokument.forsendelse.api.dto

data class FerdigstillDokumentRequest(
    val fysiskDokument: ByteArray,
    val redigeringMetadata: String? = null
)