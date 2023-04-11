package no.nav.bidrag.dokument.forsendelse.api

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRedigeringMetadataResponsDto
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.FerdigstillDokumentRequest
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.RedigerDokumentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@ForsendelseApiKontroller
@RequestMapping("/api/forsendelse/redigering")
@Timed
class RedigerDokumentKontroller(
    private val redigerDokumentService: RedigerDokumentService
) {
    @PatchMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Oppdater dokument redigeringdata",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Redigering metadata oppdatert"),
            ApiResponse(responseCode = "404", description = "Fant ingen dokument med forsendelseId og dokumentreferanse i forespørsel")
        ]
    )
    fun oppdaterDokumentRedigeringmetadata(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
        @RequestBody forespørsel: String
    ) {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return redigerDokumentService.oppdaterDokumentRedigeringMetadata(forsendelseId, dokumentreferanse, forespørsel)
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}/ferdigstill")
    @Operation(
        summary = "Ferdigstill dokument i en forsendelse",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dokument ferdigstilt"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id")
        ]
    )
    fun ferdigstillDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
        @RequestBody ferdigstillDokumentRequest: FerdigstillDokumentRequest
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return redigerDokumentService.ferdigstillDokument(forsendelseId, dokumentreferanse, ferdigstillDokumentRequest)
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}/ferdigstill/opphev")
    @Operation(
        summary = "Ferdigstill dokument i en forsendelse",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dokument ferdigstilt"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id")
        ]
    )
    fun opphevFerdigstillDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return redigerDokumentService.opphevFerdigstillDokument(forsendelseId, dokumentreferanse)
    }

    @GetMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Hent dokument redigering metadata",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Redigering metadata hentet"),
            ApiResponse(responseCode = "404", description = "Fant ingen dokument med forsendelseId og dokumentreferanse i forespørsel")
        ]
    )
    fun hentDokumentRedigeringMetadata(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String
    ): DokumentRedigeringMetadataResponsDto {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return redigerDokumentService.hentDokumentredigeringMetadata(forsendelseId, dokumentreferanse)
    }
}
