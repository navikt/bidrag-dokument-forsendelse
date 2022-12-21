package no.nav.bidrag.dokument.forsendelse.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.model.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.model.FORSENDELSEID_PREFIX
import no.nav.bidrag.dokument.forsendelse.model.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.model.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.model.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.OpprettForsendelseSvar
import no.nav.bidrag.dokument.forsendelse.service.ForsendelseInfoService
import no.nav.bidrag.dokument.forsendelse.service.OppdaterForsendelseService
import no.nav.bidrag.dokument.forsendelse.service.OpprettForsendelseService
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@RestController
@Unprotected
class EndreForsendelseKontroller(
    val oppdaterForsendelseService: OppdaterForsendelseService,
    val opprettForsendelseService: OpprettForsendelseService,
) {

    @PostMapping
    @Operation(
        description = "Oppretter forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse opprettet"),
            ApiResponse(responseCode = "400", description = "Forespørselen inneholder ugyldig verdi"),
        ]
    )
    fun opprettForsendelse(@Valid @RequestBody request: OpprettForsendelseForespørsel): OpprettForsendelseSvar {
        return opprettForsendelseService.opprettForsendelse(request)
    }

    @PutMapping("/{forsendelseIdMedPrefix}")
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
    fun oppdaterForsendelse(@PathVariable forsendelseIdMedPrefix: String, @RequestBody request: OppdaterForsendelseForespørsel): ResponseEntity<OppdaterForsendelseResponse> {
        val forsendelseId = if (forsendelseIdMedPrefix.startsWith(FORSENDELSEID_PREFIX)) forsendelseIdMedPrefix.replace(FORSENDELSEID_PREFIX, "").toLong() else forsendelseIdMedPrefix.toLong()
        val respons = oppdaterForsendelseService.oppdaterForsendelse(forsendelseId, request) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(respons)
    }

    @PutMapping("/{forsendelseIdMedPrefix}/dokument")
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
    fun knyttTilDokument(@PathVariable forsendelseIdMedPrefix: String, @RequestBody request: OpprettDokumentForespørsel): ResponseEntity<DokumentRespons> {
        val forsendelseId = if (forsendelseIdMedPrefix.startsWith(FORSENDELSEID_PREFIX)) forsendelseIdMedPrefix.replace(FORSENDELSEID_PREFIX, "").toLong() else forsendelseIdMedPrefix.toLong()
        val respons = oppdaterForsendelseService.knyttDokumentTilForsendelse(forsendelseId, request) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(respons)
    }

    @DeleteMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}")
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
    fun fjernDokumentFraForsendelse(@PathVariable forsendelseIdMedPrefix: String, @PathVariable dokumentreferanse: String): ResponseEntity<OppdaterForsendelseResponse> {
        val forsendelseId = if (forsendelseIdMedPrefix.startsWith(FORSENDELSEID_PREFIX)) forsendelseIdMedPrefix.replace(FORSENDELSEID_PREFIX, "").toLong() else forsendelseIdMedPrefix.toLong()
        val respons = oppdaterForsendelseService.fjernDokumentFraForsendelse(forsendelseId, dokumentreferanse) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(respons)
    }

}