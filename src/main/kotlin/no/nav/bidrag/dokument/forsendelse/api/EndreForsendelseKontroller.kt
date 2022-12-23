package no.nav.bidrag.dokument.forsendelse.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.tjeneste.OppdaterForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.OpprettForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.valider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import javax.validation.Valid

@ForsendelseApiKontroller
class EndreForsendelseKontroller(
    val oppdaterForsendelseTjeneste: OppdaterForsendelseTjeneste,
    val opprettForsendelseService: OpprettForsendelseTjeneste,
) {

    @PostMapping
    @Operation(
        summary = "Oppretter ny forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse opprettet"),
            ApiResponse(responseCode = "400", description = "Forespørselen inneholder ugyldig verdi"),
        ]
    )
    fun opprettForsendelse(@Valid @RequestBody request: OpprettForsendelseForespørsel): OpprettForsendelseRespons {
        return opprettForsendelseService.opprettForsendelse(request)
    }

    @PutMapping("/{forsendelseIdMedPrefix}")
    @Operation(
        summary = "Endre forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse endret"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for forsendelseId"),
        ]
    )
    fun oppdaterForsendelse(@PathVariable forsendelseIdMedPrefix: String, @RequestBody request: OppdaterForsendelseForespørsel): ResponseEntity<OppdaterForsendelseResponse> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        val respons = oppdaterForsendelseTjeneste.oppdaterForsendelse(forsendelseId, request) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(respons)
    }

    @PostMapping("/{forsendelseIdMedPrefix}/dokument")
    @Operation(
        summary = "Knytt eller opprett ny dokument til forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ]
    )
    fun knyttTilDokument(@PathVariable forsendelseIdMedPrefix: String, @RequestBody request: OpprettDokumentForespørsel): ResponseEntity<DokumentRespons> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        request.valider()
        val respons = oppdaterForsendelseTjeneste.knyttDokumentTilForsendelse(forsendelseId, request) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(respons)
    }

    @DeleteMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Slett dokument fra forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ]
    )
    fun fjernDokumentFraForsendelse(@PathVariable forsendelseIdMedPrefix: String, @PathVariable dokumentreferanse: String): ResponseEntity<OppdaterForsendelseResponse> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        val respons = oppdaterForsendelseTjeneste.fjernDokumentFraForsendelse(forsendelseId, dokumentreferanse) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(respons)
    }

    @DeleteMapping("/{forsendelseIdMedPrefix}")
    @Operation(
        summary = "Avbryt forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse avbrutt"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ]
    )
    fun avbrytForsendelse(@PathVariable forsendelseIdMedPrefix: String): ResponseEntity<OppdaterForsendelseResponse> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        val result = oppdaterForsendelseTjeneste.avbrytForsendelse(forsendelseId)
        return if (result) ResponseEntity.ok().build() else ResponseEntity.noContent().build()
    }

}