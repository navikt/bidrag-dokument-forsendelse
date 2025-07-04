package no.nav.bidrag.dokument.forsendelse.api.innsyn

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.bidrag.dokument.forsendelse.api.ForsendelseApiKontroller
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.ForsendelseInnsynService
import no.nav.bidrag.transport.dokument.JournalpostDto
import no.nav.bidrag.transport.dokument.JournalpostResponse
import no.nav.bidrag.transport.dokument.forsendelse.ForsendelseIkkeDistribuertResponsTo
import no.nav.bidrag.transport.dokument.forsendelse.JournalTema
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@ForsendelseApiKontroller
@Timed
class ForsendelseJournalKontroller(
    val forsendelseInnsynService: ForsendelseInnsynService,
) {
    @GetMapping("/journal/{forsendelseIdMedPrefix}")
    @Operation(description = "Hent forsendelse med forsendelseid")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "404", description = "Fant ingen forsendelse for forsendelseid"),
        ],
    )
    fun hentForsendelse(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @Parameter(
            name = "saksnummer",
            description = "journalposten tilhører sak",
        )
        @RequestParam(required = false)
        saksnummer: String?,
    ): JournalpostResponse {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return forsendelseInnsynService.hentForsendelseJournal(forsendelseId, saksnummer)
    }

    @GetMapping("/sak/{saksnummer}/journal")
    @Operation(
        description = "Hent alle forsendelse som har saksnummer",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forsendelser hentet. Returnerer tom liste hvis ingen forsendelser for saksnummer funnet.",
            ),
        ],
    )
    fun hentJournal(
        @PathVariable saksnummer: String,
        @RequestParam(name = "fagomrade") temaListe: List<JournalTema> = emptyList(),
    ): List<JournalpostDto> = forsendelseInnsynService.hentForsendelseForSakJournal(saksnummer, temaListe)

    @GetMapping("/journal/ikkedistribuert")
    @Operation(
        description = "Hent alle forsendelse som er opprettet før dagens dato og ikke er distribuert",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forsendelser hentet. Returnerer tom liste hvis ingen forsendelser funnet.",
            ),
        ],
    )
    fun hentForsendelserIkkeDistribuert(): List<ForsendelseIkkeDistribuertResponsTo> =
        forsendelseInnsynService.hentForsendelserIkkeDistribuert()
}
