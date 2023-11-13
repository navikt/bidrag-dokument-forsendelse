package no.nav.bidrag.dokument.forsendelse.api.admin

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.hendelse.ForsendelseSkedulering
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.security.token.support.core.api.Protected
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@Protected
@RequestMapping("/api/forsendelse/internal")
@Timed
class AdminController(private val forsendelseSkedulering: ForsendelseSkedulering) {

    @GetMapping("/distribusjon/navno")
    @Operation(
        summary = "Utfør avvikshåndtering",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(value = [ApiResponse(responseCode = "400", description = "Fant ikke forsendelse med oppgitt forsendelsid")])
    fun distTilNavNoMenHarKanalSentralPrint(): List<Forsendelse> {
        return forsendelseSkedulering.resynkDistribusjoninfoNavNo()
    }
}