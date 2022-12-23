package no.nav.bidrag.dokument.forsendelse.api.innsyn

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.ForsendelseApiKontroller
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.tjeneste.ForsendelseInnsynTjeneste
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

@ForsendelseApiKontroller
@RequestMapping("/legacy")
class ForsendelseLegacyInnsynKontroller(val forsendelseInnsynTjeneste: ForsendelseInnsynTjeneste) {

    @GetMapping("/{forsendelseIdMedPrefix}")
    @Operation(description = "Hent forsendelse med forsendelseid")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for forsendelseid"),
        ]
    )
    fun hentForsendelse(@PathVariable forsendelseIdMedPrefix: String): ResponseEntity<JournalpostResponse> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        val respons = forsendelseInnsynTjeneste.hentForsendelseLegacy(forsendelseId) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(respons)
    }

    @GetMapping("/sak/{saksnummer}/journal")
    @Operation(
        description = "Hent alle forsendelse som har saksnummer",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelser hentet. Returnerer tom liste hvis ingen forsendelser for saksnummer funnet."),
        ]
    )
    fun hentJournal(@PathVariable saksnummer: String): List<JournalpostDto> {
        return forsendelseInnsynTjeneste.hentForsendelseForSakLegacy(saksnummer)
    }
}