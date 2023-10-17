package no.nav.bidrag.dokument.forsendelse.api

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.commons.web.EnhetFilter
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.AvvikService
import no.nav.bidrag.transport.dokument.AvvikType
import no.nav.bidrag.transport.dokument.Avvikshendelse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

@ForsendelseApiKontroller
@Timed
class AvvikKontroller(val avvikService: AvvikService) {

    @PostMapping("/journal/{forsendelseIdMedPrefix}/avvik")
    @Operation(
        summary = "Utfør avvikshåndtering",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(value = [ApiResponse(responseCode = "400", description = "Fant ikke forsendelse med oppgitt forsendelsid")])
    fun utførAvvik(
        @RequestHeader(EnhetFilter.X_ENHET_HEADER, required = false) enhet: String?,
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @RequestBody avvikshendelse: Avvikshendelse
    ) {
        return avvikService.utførAvvik(forsendelseIdMedPrefix.numerisk, avvikshendelse, enhet)
    }

    @GetMapping("/journal/{forsendelseIdMedPrefix}/avvik")
    @Operation(description = "Hent gyldige avvikstyper for forsendelse")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Liste med gyldige avvikstyper"),
            ApiResponse(responseCode = "400", description = "Fant ikke forsendelse med oppgitt forsendelsid")
        ]
    )
    fun hentAvvik(@PathVariable forsendelseIdMedPrefix: ForsendelseId): List<AvvikType> {
        return avvikService.hentAvvik(forsendelseIdMedPrefix.numerisk)
    }
}
