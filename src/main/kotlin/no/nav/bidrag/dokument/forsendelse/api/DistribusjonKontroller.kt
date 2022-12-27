package no.nav.bidrag.dokument.forsendelse.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.tjeneste.DistribusjonTjeneste
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@ForsendelseApiKontroller
class DistribusjonKontroller(val distribusjonTjeneste: DistribusjonTjeneste) {

    @GetMapping("/journal/distribuer/{forsendelseIdMedPrefix}/enabled")
    @Operation(
        summary = "Sjekk om forsendelse kan distribueres",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Kan distribueres"),
            ApiResponse(responseCode = "406", description = "Kan ikke distribueres. Dette kan skyldes at forsendelsen ikke er ferdigstilt eller en eller flere av dokumentene ikke er ferdigstilt"),
        ]
    )
    fun kanDistribuere(@PathVariable forsendelseIdMedPrefix: String): ResponseEntity<Void> {
        return if (distribusjonTjeneste.kanDistribuere(forsendelseIdMedPrefix.numerisk)) ResponseEntity.ok().build()
        else ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build()
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
    fun distribuerForsendelse(@RequestBody(required = false) distribuerJournalpostRequest: DistribuerJournalpostRequest?,
                              @PathVariable forsendelseIdMedPrefix: String,
                              @RequestParam(required = false) batchId: String?): ResponseEntity<DistribuerJournalpostResponse>{


        val response = distribusjonTjeneste.distribuer(forsendelseIdMedPrefix.numerisk, distribuerJournalpostRequest)
        return if (response != null) ResponseEntity.ok(response) else ResponseEntity.badRequest().build()
    }
}