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
        summary = "Hent fysisk dokument som byte",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentDokument(@PathVariable forsendelseIdMedPrefix: String, @PathVariable dokumentreferanse: String): ByteArray {
        return hentDokumentTjeneste.hentDokument(forsendelseIdMedPrefix.numerisk, dokumentreferanse)
    }
}