package no.nav.bidrag.dokument.forsendelse.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.model.FORSENDELSEID_PREFIX
import no.nav.bidrag.dokument.forsendelse.model.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.model.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.OpprettForsendelseSvar
import no.nav.bidrag.dokument.forsendelse.service.ForsendelseInfoService
import no.nav.bidrag.dokument.forsendelse.service.OppdaterForsendelseService
import no.nav.bidrag.dokument.forsendelse.service.OpprettForsendelseService
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@RestController
@Unprotected
class ForsendelseKontroller(val forsendelseInfoService: ForsendelseInfoService) {

    @GetMapping("/{forsendelseIdMedPrefix}")
    @Operation(
        description = "Hent forsendelse med id",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ]
    )
    fun hentForsendelse(@PathVariable forsendelseIdMedPrefix: String): ResponseEntity<JournalpostResponse> {
        val forsendelseId = if (forsendelseIdMedPrefix.startsWith(FORSENDELSEID_PREFIX)) forsendelseIdMedPrefix.replace(FORSENDELSEID_PREFIX, "").toLong() else forsendelseIdMedPrefix.toLong()
        val respons = forsendelseInfoService.hentForsendelse(forsendelseId) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(respons)
    }

    @GetMapping("/sak/{saksnummer}/journal")
    @Operation(
        description = "Hent forsendelse med i",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse opprettet"),
            ApiResponse(responseCode = "400", description = "Forespørselen inneholder ugyldig verdi"),
        ]
    )
    fun hentJournal(@PathVariable saksnummer: String): List<JournalpostDto> {
        return forsendelseInfoService.hentForsendelseForSak(saksnummer)
    }
}