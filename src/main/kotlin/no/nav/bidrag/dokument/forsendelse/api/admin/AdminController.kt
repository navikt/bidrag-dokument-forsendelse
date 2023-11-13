package no.nav.bidrag.dokument.forsendelse.api.admin

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.hendelse.ForsendelseSkedulering
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.security.token.support.core.api.Protected
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

data class ForsendelseMetadata(
    val forsendelseId: Long?,
    val joarkJournalpostId: String?,
    val saksnummer: String?,
    val enhet: String?,
    val gjelderIdent: String?,
    val mottakerId: String?,
    val saksbehandlerIdent: String?,
    val saksbehandlerNavn: String?,
    val distribuertDato: LocalDateTime?,
    val kanal: DistribusjonKanal?
)

@RestController
@Protected
@RequestMapping("/api/forsendelse/internal")
@Timed
class AdminController(private val forsendelseSkedulering: ForsendelseSkedulering) {

    @GetMapping("/distribusjon/navno")
    @Operation(
        summary = "Resynk distribuert kanal",
        security = [SecurityRequirement(name = "bearer-key")]
    )
    @ApiResponses(value = [ApiResponse(responseCode = "400", description = "Fant ikke forsendelse med oppgitt forsendelsid")])
    fun distTilNavNoMenHarKanalSentralPrint(
        @RequestParam(
            required = false,
            defaultValue = "true"
        ) simulering: Boolean = true
    ): List<ForsendelseMetadata> {
        return forsendelseSkedulering.resynkDistribusjoninfoNavNo(simulering).map {
            ForsendelseMetadata(
                it.forsendelseId,
                it.journalpostIdFagarkiv,
                it.saksnummer,
                it.enhet,
                it.gjelderIdent,
                it.mottaker?.ident,
                it.distribuertAvIdent,
                it.opprettetAvNavn,
                it.distribuertTidspunkt,
                it.distribusjonKanal
            )
        }
    }
}
