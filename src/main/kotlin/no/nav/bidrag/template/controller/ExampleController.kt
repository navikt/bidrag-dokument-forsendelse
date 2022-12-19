package no.nav.bidrag.template.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.template.model.HentPersonResponse
import no.nav.bidrag.template.service.ExampleService
import no.nav.security.token.support.core.api.Protected
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Protected
class ExampleController(
    var exampleService: ExampleService
) {

    @GetMapping("/{personId}")
    @Operation(
        description = "Henter person data",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet person data"),
            ApiResponse(responseCode = "404", description = "Fant ikke person"),
            ApiResponse(responseCode = "401", description = "Sikkerhetstoken er ikke gyldig"),
            ApiResponse(
                responseCode = "403",
                description = "Sikkerhetstoken er ikke gyldig, eller det er ikke gitt adgang til kode 6 og 7 (nav-ansatt)"
            ),
        ]
    )
    fun hentDialog(@PathVariable personId: String): HentPersonResponse? {
        return exampleService.hentDialogerForPerson(personId)
    }

}