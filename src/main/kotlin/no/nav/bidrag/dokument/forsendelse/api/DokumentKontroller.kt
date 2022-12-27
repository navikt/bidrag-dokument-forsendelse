package no.nav.bidrag.dokument.forsendelse.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.tjeneste.DistribusjonTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.ForsendelseInnsynTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.HentDokumentTjeneste
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@ForsendelseApiKontroller
class DokumentKontroller(val hentDokumentTjeneste: HentDokumentTjeneste) {

    @GetMapping("/dokument/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Hent dokument",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Kan distribueres"),
            ApiResponse(responseCode = "406", description = "Kan ikke distribueres. Dette kan skyldes at forsendelsen ikke er ferdigstilt eller en eller flere av dokumentene ikke er ferdigstilt"),
        ]
    )
    fun hentDOkument(@PathVariable forsendelseIdMedPrefix: String, @PathVariable dokumentreferanse: String): ByteArray {
        return hentDokumentTjeneste.hentDokument(forsendelseIdMedPrefix.numerisk, dokumentreferanse)
    }
}