package no.nav.bidrag.dokument.forsendelse.api

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.dto.EndreJournalpostCommand
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.utvidelser.tilOppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.OppdaterForsendelseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@ForsendelseApiKontroller
@Timed
class EndreForsendelseKontroller(val oppdaterForsendelseService: OppdaterForsendelseService) {

    @PatchMapping("/{forsendelseIdMedPrefix}")
    @Operation(
        summary = "Endre forsendelse",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse endret"),
            ApiResponse(responseCode = "404", description = "Fant ingen forsendelse for forsendelseId")
        ]
    )
    fun oppdaterForsendelse(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @RequestBody request: OppdaterForsendelseForespørsel
    ): OppdaterForsendelseResponse {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return oppdaterForsendelseService.oppdaterForsendelse(forsendelseId, request)
    }

    @PatchMapping("/journal/{forsendelseIdMedPrefix}")
    @Operation(
        summary = "Endre forsendelse",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse endret"),
            ApiResponse(responseCode = "404", description = "Fant ingen forsendelse for forsendelseId")
        ]
    )
    fun oppdaterForsendelseLegacy(@PathVariable forsendelseIdMedPrefix: ForsendelseId, @RequestBody request: EndreJournalpostCommand) {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        oppdaterForsendelseService.oppdaterForsendelse(forsendelseId, request.tilOppdaterForsendelseForespørsel())
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/dokument/{dokumentreferanse}")
    @Operation(
        summary = "Oppdater dokument i en forsendelsee",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id")
        ]
    )
    fun oppdaterDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
        @RequestBody forespørsel: OppdaterDokumentForespørsel
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return oppdaterForsendelseService.oppdaterDokument(forsendelseId, dokumentreferanse, forespørsel)
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/dokument/{dokumentreferanse}/ferdigstill")
    @Operation(
        summary = "Ferdigstill dokument i en forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dokument ferdigstilt"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ]
    )
    fun ferdigstillDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return oppdaterForsendelseService.ferdigstillDokument(forsendelseId, dokumentreferanse)
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/dokument/{dokumentreferanse}/opphevFerdigstill")
    @Operation(
        summary = "Opphev ferdigstilling av dokument i en forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dokument ferdigstilt"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ]
    )
    fun opphevFerdigstillingAvDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return oppdaterForsendelseService.opphevFerdigstillingAvDokument(forsendelseId, dokumentreferanse)
    }

    @PostMapping("/{forsendelseIdMedPrefix}/dokument")
    @Operation(
        summary = "Knytt eller opprett ny dokument til forsendelse",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id")
        ]
    )
    fun knyttTilDokument(@PathVariable forsendelseIdMedPrefix: ForsendelseId, @RequestBody forespørsel: OpprettDokumentForespørsel): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return oppdaterForsendelseService.knyttDokumentTilForsendelse(forsendelseId, forespørsel)
    }

    @DeleteMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Slett dokument fra forsendelse",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id")
        ]
    )
    fun fjernDokumentFraForsendelse(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String
    ): ResponseEntity<OppdaterForsendelseResponse> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        val respons = oppdaterForsendelseService.fjernDokumentFraForsendelse(forsendelseId, dokumentreferanse)
            ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(respons)
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/ferdigstill")
    @Operation(
        summary = "Ferdigstiller forsendelse ved å arkivere forsendelsen i Joark og dermed klargjør for eventuell distribuering",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse er ferdigstilt"),
            ApiResponse(responseCode = "400", description = "Fant ingen forsendelse for id")
        ]
    )
    fun ferdigstillForsendelse(@PathVariable forsendelseIdMedPrefix: ForsendelseId): ResponseEntity<OpprettJournalpostResponse> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        val result = oppdaterForsendelseService.ferdigstillForsendelse(forsendelseId)
        return if (result != null) ResponseEntity.ok(result) else ResponseEntity.badRequest().build()
    }
}
