package no.nav.bidrag.dokument.forsendelse.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.mapper.tilEttersendingsoppaveDto
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.EttersendingsOppgaveService
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.transport.dokument.forsendelse.EttersendingsoppgaveDto
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterEttersendingsoppgaveRequest
import no.nav.bidrag.transport.dokument.forsendelse.OpprettEttersendingsoppgaveRequest
import no.nav.bidrag.transport.dokument.forsendelse.SlettEttersendingsoppgave
import no.nav.bidrag.transport.dokument.forsendelse.SlettEttersendingsoppgaveVedleggRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

private val log = KotlinLogging.logger {}

@ForsendelseApiKontroller
class EttersendingsoppgaveController(
    val ettersendingsOppgaveService: EttersendingsOppgaveService,
    val forsenelseTjeneste: ForsendelseTjeneste,
) {
    @PostMapping("/ettersendingsoppgave")
    @Operation(
        summary = "Oppretter ny ettersendingsoppgave",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun opprettEttersendingsoppgave(
        @Valid @RequestBody
        request: OpprettEttersendingsoppgaveRequest,
    ): EttersendingsoppgaveDto {
        SIKKER_LOGG.info { "Oppretter ny ettersendingsoppgave $request" }
        val forsendelse = forsenelseTjeneste.medForsendelseId(request.forsendelseId)!!
        ettersendingsOppgaveService.opprettEttersendingsoppgave(request)
        return forsendelse.ettersendingsoppgave!!.tilEttersendingsoppaveDto()
    }

    @PutMapping("/ettersendingsoppgave")
    @Operation(
        summary = "Oppretter ny varsel ettersendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdaterEttesendingsoppgave(
        @Valid @RequestBody
        request: OppdaterEttersendingsoppgaveRequest,
    ): EttersendingsoppgaveDto {
        SIKKER_LOGG.info { "Oppdaterer ettersendingsoppave $request" }
        val forsendelse = forsenelseTjeneste.medForsendelseId(request.forsendelseId)!!
        ettersendingsOppgaveService.oppdaterEttersendingsoppgave(request)
        return forsendelse.ettersendingsoppgave!!.tilEttersendingsoppaveDto()
    }

    @DeleteMapping("/ettersendingsoppgave")
    @Operation(
        summary = "Oppretter ny ettersendingsoppave",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse opprettet"),
            ApiResponse(responseCode = "400", description = "Forespørselen inneholder ugyldig verdi"),
        ],
    )
    fun slettEttersendingsoppgave(
        @Valid @RequestBody
        request: SlettEttersendingsoppgave,
    ) {
        secureLogger.info { "Slett ettersendingsoppgave $request" }
        ettersendingsOppgaveService.slettEttersendingsoppave(request)
    }

    @DeleteMapping("/ettersendingsoppgave/dokument")
    @Operation(
        summary = "Oppretter ny varsel ettersendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse opprettet"),
            ApiResponse(responseCode = "400", description = "Forespørselen inneholder ugyldig verdi"),
        ],
    )
    fun slettEttersendingsoppgaveVedlegg(
        @Valid @RequestBody
        request: SlettEttersendingsoppgaveVedleggRequest,
    ): EttersendingsoppgaveDto {
        log.info { "Sletter varsel ettersendelse $request" }
        val forsendelse = forsenelseTjeneste.medForsendelseId(request.forsendelseId)!!
        ettersendingsOppgaveService.slettEttersendingsoppgaveVedlegg(request)
        return forsendelse.ettersendingsoppgave!!.tilEttersendingsoppaveDto()
    }

    @GetMapping("/ettersendingsoppgave/oppgaver/{forsendelseId}")
    @Operation(
        summary = "Hent ettersendingsoppgaver",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentEksisterendeEttersendingsoppgaverForsendelse(
        @PathVariable forsendelseId: String,
    ) = ettersendingsOppgaveService.hentEksisterendeEttersendingsoppgaverForBruker(forsendelseId.numerisk)
}
