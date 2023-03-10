package no.nav.bidrag.dokument.forsendelse.api

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespĂ¸rsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.service.OpprettForsendelseService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import javax.validation.Valid

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
            ApiResponse(responseCode = "400", description = "ForespĂ¸rselen inneholder ugyldig verdi"),
        ]
    )
    fun opprettForsendelse(@Valid @RequestBody request: OpprettForsendelseForespĂ¸rsel): OpprettForsendelseRespons {
        SIKKER_LOGG.info("Oppretter ny forsendelse $request")
        return opprettForsendelseService.opprettForsendelse(request)
    }

}