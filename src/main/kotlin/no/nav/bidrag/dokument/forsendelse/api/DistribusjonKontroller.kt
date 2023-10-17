package no.nav.bidrag.dokument.forsendelse.api

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.DistribusjonService
import no.nav.bidrag.transport.dokument.DistribuerJournalpostRequest
import no.nav.bidrag.transport.dokument.DistribuerJournalpostResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@ForsendelseApiKontroller
@Timed
class DistribusjonKontroller(val distribusjonService: DistribusjonService) {

    @GetMapping("/journal/distribuer/{forsendelseIdMedPrefix}/size")
    @Operation(
        summary = "Hent størrelse på dokumentene i forsendelsen",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    fun henStørrelsePåDokumenter(@PathVariable forsendelseIdMedPrefix: ForsendelseId): Long {
        return distribusjonService.størrelseIMb(forsendelseIdMedPrefix.numerisk)
    }

    @GetMapping("/journal/distribuer/{forsendelseIdMedPrefix}/enabled")
    @Operation(
        summary = "Sjekk om forsendelse kan distribueres",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Kan distribueres"),
            ApiResponse(
                responseCode = "406",
                description = "Kan ikke distribueres. Dette kan skyldes at forsendelsen ikke er ferdigstilt eller en eller flere av dokumentene ikke er ferdigstilt"
            )
        ]
    )
    fun kanDistribuere(@PathVariable forsendelseIdMedPrefix: ForsendelseId): ResponseEntity<String> {
        return try {
            distribusjonService.validerKanDistribuere(forsendelseIdMedPrefix.numerisk)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(e.message)
        }
    }

    @PostMapping("/journal/distribuer/{forsendelseIdMedPrefix}")
    @Operation(description = "Bestill distribusjon av forsendelse")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Distribusjon av journalpost er bestilt"),
            ApiResponse(responseCode = "400", description = "Journalpost mangler mottakerid eller adresse er ikke oppgitt i kallet"),
            ApiResponse(responseCode = "404", description = "Fant ikke journalpost som skal distribueres")
        ]
    )
    fun distribuerForsendelse(
        @RequestBody(required = false) distribuerJournalpostRequest: DistribuerJournalpostRequest?,
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @RequestParam(required = false) batchId: String?,
        @RequestParam(required = false) ingenDistribusjon: Boolean = false
    ): DistribuerJournalpostResponse {
        return distribusjonService.distribuer(forsendelseIdMedPrefix.numerisk, distribuerJournalpostRequest, batchId, ingenDistribusjon)
    }
}
