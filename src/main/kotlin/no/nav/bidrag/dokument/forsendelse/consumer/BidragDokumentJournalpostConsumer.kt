package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.transport.dokument.DistribuerJournalpostRequest
import no.nav.bidrag.transport.dokument.DistribuerJournalpostResponse
import no.nav.bidrag.transport.dokument.DistribuerTilAdresse
import no.nav.bidrag.transport.dokument.DistribusjonInfoDto
import no.nav.bidrag.transport.dokument.DokumentMetadata
import no.nav.bidrag.transport.dokument.JournalpostDto
import no.nav.bidrag.transport.dokument.OpprettEttersendingsppgaveDto
import no.nav.bidrag.transport.dokument.OpprettJournalpostRequest
import no.nav.bidrag.transport.dokument.OpprettJournalpostResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate

data class OppdaterStatusPåDokumenterUnderProduksjonResultDto(
    val journalposterMedDokumentSomErFerdigstilt: MutableList<JournalpostDokumentDto>,
    val journalposterMedDokumentSomIkkeErFerdigstilt: MutableList<JournalpostDokumentDto>,
    val journalposterHvorDokumentErSlettet: MutableList<JournalpostDokumentDto>,
) {
    data class JournalpostDokumentDto(
        val journalpostId: Int,
        val dokumentreferanse: String,
        val tittel: String?,
        val brevkode: String?,
        val journalforendeEnhet: String?,
        val journalforendeEnhetNavn: String?,
        val dokumentdato: LocalDate?,
        val journaldato: LocalDate?,
        val doktype: String,
        var bleFerdigstilt: Boolean,
    )
}

data class OppdaterStatusPåDokumenterUnderProduksjonRequestDto(
    val simuler: Boolean = true,
    val startFraPeker: Int = 0,
    val sjekkForAntallJournalposter: Int = 100,
)

@Service
class BidragDokumentJournalpostConsumer(
    @Value("\${BIDRAG_DOKUMENT_JOURNALPOST_URL}") val url: URI,
    @Qualifier("azureLongerTimeout") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-dokument-journalpost") {
    private fun createUri(path: String?) =
        UriComponentsBuilder
            .fromUri(url)
            .path(path ?: "")
            .build()
            .toUri()

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun oppdaterStatusPåDokumentUnderProduksjon(): OppdaterStatusPåDokumenterUnderProduksjonResultDto =
        postForNonNullEntity<OppdaterStatusPåDokumenterUnderProduksjonResultDto>(
            UriComponentsBuilder
                .fromUri(url)
                .path("/api/admin/oppdaterStatus")
                .build()
                .toUri(),
            OppdaterStatusPåDokumenterUnderProduksjonRequestDto(false, 0, 500),
        )
}
