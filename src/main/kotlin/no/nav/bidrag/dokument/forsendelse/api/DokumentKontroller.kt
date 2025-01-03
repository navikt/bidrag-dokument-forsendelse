package no.nav.bidrag.dokument.forsendelse.api

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.FysiskDokumentService
import no.nav.bidrag.transport.dokument.DokumentMetadata
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@ForsendelseApiKontroller
@Timed
class DokumentKontroller(
    val fysiskDokumentService: FysiskDokumentService,
) {
    @GetMapping("/dokument/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Hent fysisk dokument som byte",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=$dokumentreferanse.pdf",
            ).body(fysiskDokumentService.hentDokument(forsendelseIdMedPrefix.numerisk, dokumentreferanse))

    @GetMapping("/dokumentreferanse/{dokumentreferanse}")
    @Operation(
        summary = "Hent fysisk dokument som byte",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentDokumentForReferanse(
        @PathVariable dokumentreferanse: String,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=$dokumentreferanse.pdf",
            ).body(fysiskDokumentService.hentDokument(dokumentreferanse))

    @RequestMapping(
        *["/dokument/{forsendelseIdMedPrefix}/{dokumentreferanse}", "/dokument/{forsendelseIdMedPrefix}"],
        method = [RequestMethod.OPTIONS],
    )
    @Operation(
        summary = "Hent metadata om dokument",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentDokumentMetadata(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable(required = false) dokumentreferanse: String?,
    ): List<DokumentMetadata> = fysiskDokumentService.hentDokumentMetadata(forsendelseIdMedPrefix.numerisk, dokumentreferanse)

    @RequestMapping(
        *["/dokumentreferanse/{dokumentreferanse}"],
        method = [RequestMethod.OPTIONS],
    )
    @Operation(
        summary = "Hent metadata om dokument",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentDokumentMetadataForReferanse(
        @PathVariable dokumentreferanse: String,
    ): List<DokumentMetadata> = fysiskDokumentService.hentDokumentMetadataForReferanse(dokumentreferanse)
}
