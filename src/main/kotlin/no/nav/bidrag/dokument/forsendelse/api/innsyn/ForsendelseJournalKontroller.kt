package no.nav.bidrag.dokument.forsendelse.api.innsyn

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.ForsendelseApiKontroller
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.ForsendelseInnsynTjeneste
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

@ForsendelseApiKontroller
@Timed
class ForsendelseJournalKontroller(
    val forsendelseInnsynTjeneste: ForsendelseInnsynTjeneste,
    val bidragDokumentBestillingConsumer: BidragDokumentBestillingConsumer
) {

    @GetMapping("/journal/{forsendelseIdMedPrefix}")
    @Operation(description = "Hent forsendelse med forsendelseid")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "404", description = "Fant ingen forsendelse for forsendelseid"),
        ]
    )
    fun hentForsendelse(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId, @Parameter(
            name = "saksnummer",
            description = "journalposten tilhører sak"
        ) @RequestParam(required = false) saksnummer: String?
    ): JournalpostResponse {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return forsendelseInnsynTjeneste.hentForsendelseJournal(forsendelseId, saksnummer)
    }

    @GetMapping("/sak/{saksnummer}/journal")
    @Operation(
        description = "Hent alle forsendelse som har saksnummer",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forsendelser hentet. Returnerer tom liste hvis ingen forsendelser for saksnummer funnet."
            ),
        ]
    )
    fun hentJournal(
        @PathVariable saksnummer: String,
        @RequestParam(name = "fagomrade") temaListe: List<JournalTema> = emptyList()
    ): List<JournalpostDto> {
        return forsendelseInnsynTjeneste.hentForsendelseForSakJournal(saksnummer, temaListe)
    }

    @RequestMapping("/dokumentmaler", method = [RequestMethod.OPTIONS])
    @Operation(
        description = "Henter dokumentmaler som er støttet av applikasjonen",
    )
    fun støttedeDokumentmaler(): List<String> {
        return bidragDokumentBestillingConsumer.støttedeDokumentmaler()
    }
}