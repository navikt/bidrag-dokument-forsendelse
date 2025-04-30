package no.nav.bidrag.dokument.forsendelse.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.RedigerDokumentService
import no.nav.bidrag.dokument.forsendelse.service.pdf.convertToPDFA
import no.nav.bidrag.dokument.forsendelse.service.pdf.lastOgReparerPDF
import no.nav.bidrag.dokument.forsendelse.service.pdf.validerPDFA
import no.nav.bidrag.transport.dokument.forsendelse.DokumentRedigeringMetadataResponsDto
import no.nav.bidrag.transport.dokument.forsendelse.DokumentRespons
import no.nav.bidrag.transport.dokument.forsendelse.FerdigstillDokumentRequest
import no.nav.security.token.support.core.api.Protected
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val log = KotlinLogging.logger {}

@RestController
@Protected
@RequestMapping("/api/forsendelse/redigering")
@Timed
class RedigerDokumentKontroller(
    private val redigerDokumentService: RedigerDokumentService,
) {
    @PatchMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Oppdater dokument redigeringdata",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Redigering metadata oppdatert"),
            ApiResponse(responseCode = "404", description = "Fant ingen dokument med forsendelseId og dokumentreferanse i forespørsel"),
        ],
    )
    fun oppdaterDokumentRedigeringmetadata(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
        @RequestBody forespørsel: String,
    ) {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return redigerDokumentService.oppdaterDokumentRedigeringMetadata(forsendelseId, dokumentreferanse, forespørsel)
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}/ferdigstill")
    @Operation(
        summary = "Ferdigstill dokument i en forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dokument ferdigstilt"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ],
    )
    fun ferdigstillDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
        @RequestBody ferdigstillDokumentRequest: FerdigstillDokumentRequest,
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return redigerDokumentService.ferdigstillDokument(forsendelseId, dokumentreferanse, ferdigstillDokumentRequest)
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}/ferdigstill/opphev")
    @Operation(
        summary = "Ferdigstill dokument i en forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dokument ferdigstilt"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ],
    )
    fun opphevFerdigstillDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return redigerDokumentService.opphevFerdigstillDokument(forsendelseId, dokumentreferanse)
    }

    @GetMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Hent dokument redigering metadata",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Redigering metadata hentet"),
            ApiResponse(responseCode = "404", description = "Fant ingen dokument med forsendelseId og dokumentreferanse i forespørsel"),
        ],
    )
    fun hentDokumentRedigeringMetadata(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
    ): DokumentRedigeringMetadataResponsDto {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return redigerDokumentService.hentDokumentredigeringMetadata(forsendelseId, dokumentreferanse)
    }

    @PostMapping("/reparerPDF")
    @Operation(
        summary = "Reparer PDF hvis den er korrupt",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = [
            Content(
                mediaType = "application/pdf",
                schema = Schema(type = "string", format = "binary"),
            ),
        ],
    )
    @ApiResponse(
        content = [
            Content(
                mediaType = "application/pdf",
                schema = Schema(type = "string", format = "binary"),
            ),
        ],
    )
    fun reparerPDF(
        @RequestBody pdf: ByteArray,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=dokument.pdf",
            ).body(lastOgReparerPDF(pdf))

    @OptIn(ExperimentalEncodingApi::class)
    @PostMapping("/reparerPDFBase64")
    @Operation(
        summary = "Reparer PDF hvis den er korrupt",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = [
            Content(
                mediaType = "application/pdf",
                schema = Schema(type = "string", format = "byte"),
            ),
        ],
    )
    @ApiResponse(
        content = [
            Content(
                mediaType = "application/pdf",
                schema = Schema(type = "string", format = "binary"),
            ),
        ],
    )
    fun reparerPDFBase64(
        @RequestBody base64PDF: String,
    ): ResponseEntity<ByteArray> {
        val pdf = Base64.decode(base64PDF)
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=dokument.pdf",
            ).body(lastOgReparerPDF(pdf))
    }

    @PostMapping("/validerPDF")
    @Operation(
        summary = "Valider om PDF er gyldig PDF/A dokument. Respons vil gi hva som ikke er gyldig hvis ikke gyldig PDF/A.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = [
            Content(
                mediaType = "application/pdf",
                schema = Schema(type = "string", format = "binary"),
            ),
        ],
    )
    fun validerPDF(
        @RequestBody pdf: ByteArray,
    ): String? = validerPDFA(pdf)

    @PostMapping("/convertToPDFA")
    @Operation(
        summary = "Valider om PDF er gyldig PDF/A dokument. Respons vil gi hva som ikke er gyldig hvis ikke gyldig PDF/A.",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = [
            Content(
                mediaType = "application/pdf",
                schema = Schema(type = "string", format = "binary"),
            ),
        ],
    )
    fun convertToPDFA2(
        @RequestBody pdf: ByteArray,
    ): ByteArray? = convertToPDFA(pdf, "")
}
