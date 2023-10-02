package no.nav.bidrag.dokument.forsendelse.api.innsyn

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.bidrag.dokument.forsendelse.api.ForsendelseApiKontroller
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.DokumentValgService
import no.nav.bidrag.dokument.forsendelse.service.ForsendelseInnsynService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

@ForsendelseApiKontroller
@Timed
class ForsendelseInnsynKontroller(
    val forsendelseInnsynService: ForsendelseInnsynService,
    val dokumentValgService: DokumentValgService,
    val bidragDokumentBestillingConsumer: BidragDokumentBestillingConsumer
) {

    @GetMapping("/{forsendelseIdMedPrefix}")
    @Operation(description = "Hent forsendelse med forsendelseid")
    @ApiResponses(
        value = [ApiResponse(responseCode = "404", description = "Fant ingen forsendelse for forsendelseid")]
    )
    fun hentForsendelse(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @Parameter(
            name = "saksnummer",
            description = "journalposten tilhører sak"
        )
        @RequestParam(required = false)
        saksnummer: String?
    ): ForsendelseResponsTo {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return forsendelseInnsynService.hentForsendelse(forsendelseId, saksnummer)
    }

    @GetMapping("/sak/{saksnummer}/forsendelser")
    @Operation(description = "Hent alle forsendelse med saksnummer")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Forsendelser hentet. Returnerer tom liste hvis ingen forsendelser for saksnummer funnet."
            )
        ]
    )
    fun hentJournal(@PathVariable saksnummer: String): List<ForsendelseResponsTo> {
        return forsendelseInnsynService.hentForsendelseForSak(saksnummer)
    }

    @RequestMapping("/dokumentmaler", method = [RequestMethod.OPTIONS])
    @Operation(description = "Henter dokumentmaler som er støttet av applikasjonen")
    fun støttedeDokumentmaler(): List<String> {
        return bidragDokumentBestillingConsumer.støttedeDokumentmaler()
    }

    @RequestMapping("/dokumentmaler/detaljer", method = [RequestMethod.OPTIONS])
    @Operation(description = "Henter dokumentmaler som er støttet av applikasjonen")
    fun støttedeDokumentmalDetaljer(): Map<String, DokumentMalDetaljer> {
        return bidragDokumentBestillingConsumer.dokumentmalDetaljer()
    }

    @GetMapping("/dokumentvalg/forsendelse/{forsendelseIdMedPrefix}")
    @Operation(description = "Henter dokumentmaler som er støttet av applikasjonen")
    fun hentDokumentValgForForsendelse(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId
    ): Map<String, DokumentMalDetaljer> {
        return forsendelseInnsynService.hentDokumentvalgForsendelse(forsendelseIdMedPrefix.numerisk)
    }

    @PostMapping("/dokumentvalg")
    @Operation(description = "Henter dokumentmaler som er støttet av applikasjonen")
    fun hentDokumentValg(
        @RequestBody(required = false) request: HentDokumentValgRequest? = null
    ): Map<String, DokumentMalDetaljer> {
        return dokumentValgService.hentDokumentMalListe(request)
    }

    @PostMapping("/dokumentvalg/vedlegg")
    @Operation(description = "Henter dokumentmaler for statiske vedlegg")
    fun hentDokumentValgVedlegg(
        @RequestBody(required = false) request: HentDokumentValgRequest? = null
    ): Map<String, DokumentMalDetaljer> {
        return dokumentValgService.hentDokumentMalListe(request)
    }

    @PostMapping("/dokumentvalg/notat")
    @Operation(description = "Henter dokumentmaler som er støttet av applikasjonen")
    fun hentDokumentValgNotater(@RequestBody(required = false) request: HentDokumentValgRequest? = null): Map<String, DokumentMalDetaljer> {
        return dokumentValgService.hentNotatListe(request)
    }

    @GetMapping("/dokumentvalg/notat")
    @Operation(description = "Henter dokumentmaler som er støttet av applikasjonen", deprecated = true)
    fun hentDokumentValgNotaterGet(): Map<String, DokumentMalDetaljer> {
        return dokumentValgService.hentNotatListe()
    }
}
