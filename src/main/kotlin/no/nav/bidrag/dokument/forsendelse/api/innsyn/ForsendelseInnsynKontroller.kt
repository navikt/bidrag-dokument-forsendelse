package no.nav.bidrag.dokument.forsendelse.api.innsyn

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.bidrag.dokument.forsendelse.api.ForsendelseApiKontroller
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingKonsumer
import no.nav.bidrag.dokument.forsendelse.model.FORSENDELSE_ID
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.ForsendelseInnsynTjeneste
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@ForsendelseApiKontroller
@RequestMapping("/api/forsendelse/v2")
class ForsendelseInnsynKontroller(val forsendelseInnsynTjeneste: ForsendelseInnsynTjeneste, val bidragDokumentBestillingKonsumer: BidragDokumentBestillingKonsumer) {

    @GetMapping("/{forsendelseIdMedPrefix}")
    @Operation(description = "Hent forsendelse med forsendelseid")
    @ApiResponses(
        value = [ApiResponse(responseCode = "404", description = "Fant ingen forsendelse for forsendelseid")]
    )
    fun hentForsendelse(@PathVariable forsendelseIdMedPrefix: FORSENDELSE_ID): ResponseEntity<ForsendelseResponsTo> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        val respons = forsendelseInnsynTjeneste.hentForsendelse(forsendelseId) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(respons)
    }

    @GetMapping("/sak/{saksnummer}/journal")
    @Operation(description = "Hent alle forsendelse med saksnummer",)
    @ApiResponses(
        value = [ApiResponse(responseCode = "200", description = "Forsendelser hentet. Returnerer tom liste hvis ingen forsendelser for saksnummer funnet.")]
    )
    fun hentJournal(@PathVariable saksnummer: String): List<ForsendelseResponsTo> {
        return forsendelseInnsynTjeneste.hentForsendelseForSak(saksnummer)
    }

    @RequestMapping("/dokumentmaler", method = [RequestMethod.OPTIONS])
    @Operation(description = "Henter dokumentmaler som er støttet av applikasjonen")
    fun støttedeDokumentmaler(): List<String> {
        return bidragDokumentBestillingKonsumer.støttedeDokumentmaler()
    }
}