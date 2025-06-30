package no.nav.bidrag.dokument.forsendelse.api

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.model.ConflictException
import no.nav.bidrag.dokument.forsendelse.service.OpprettForsendelseService
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseRespons
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@ForsendelseApiKontroller
@Timed
class OpprettForsendelseKontroller(
    val opprettForsendelseService: OpprettForsendelseService,
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
            ApiResponse(
                responseCode = "412",
                description = "Angitt sisteVedtaksid er ikke nyeste vedtak",
                content = [
                    Content(
                        schema = Schema(implementation = ConflictException::class),
                    ),
                ],
            ),
        ],
    )
    fun opprettForsendelse(
        @Valid @RequestBody
        request: OpprettForsendelseForespørsel,
    ): OpprettForsendelseRespons {
        SIKKER_LOGG.info { "Oppretter ny forsendelse $request" }
        try {
            return opprettForsendelseService.opprettForsendelse(request)
        } catch (e: DataIntegrityViolationException) {
            opprettForsendelseService.behandleDataIntegrityException(e, request)
        }
    }
}
