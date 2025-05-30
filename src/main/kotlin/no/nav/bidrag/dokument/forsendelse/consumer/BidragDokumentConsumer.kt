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

@Service
class BidragDokumentConsumer(
    @Value("\${BIDRAG_DOKUMENT_URL}") val url: URI,
    @Qualifier("azureLongerTimeout") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-dokument") {
    private fun createUri(path: String?) =
        UriComponentsBuilder
            .fromUri(url)
            .path(path ?: "")
            .build()
            .toUri()

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun hentJournal(saksnummer: String): List<JournalpostDto> =
        getForNonNullEntity(
            UriComponentsBuilder
                .fromUri(url)
                .path("/sak/$saksnummer/journal")
                .queryParam("fagomrade", "BID")
                .queryParam("fagomrade", "FAR")
                .build()
                .toUri(),
        )

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun opprettJournalpost(opprettJournalpostRequest: OpprettJournalpostRequest): OpprettJournalpostResponse? =
        postForEntity(createUri("/journalpost/JOARK"), opprettJournalpostRequest)

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun erFerdigstilt(dokumentreferanse: String): Boolean =
        getForNonNullEntity(createUri("/dokumentreferanse/$dokumentreferanse/erFerdigstilt"))

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun hentDokumentMetadata(
        journalpostId: String,
        dokumentId: String?,
    ): List<DokumentMetadata> =
        optionsForEntity(
            UriComponentsBuilder
                .fromUri(url)
                .path("/dokument/$journalpostId${dokumentId?.let { "/$it" } ?: ""}")
                .build()
                .toUri(),
        ) ?: emptyList()

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 300, maxDelay = 2000, multiplier = 2.0))
    fun hentDokument(
        journalpostId: String?,
        dokumentId: String?,
    ): ByteArray? {
        if (journalpostId.isNullOrEmpty()) return hentDokument(dokumentId)
        return getForEntity(
            UriComponentsBuilder
                .fromUri(url)
                .path("/dokument/$journalpostId${dokumentId?.let { "/$it" } ?: ""}")
                .queryParam("optimizeForPrint", "false")
                .build()
                .toUri(),
        )
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun hentDokument(dokumentId: String?): ByteArray? =
        getForEntity(
            UriComponentsBuilder
                .fromUri(url)
                .path("/dokumentreferanse/$dokumentId")
                .queryParam("optimizeForPrint", "false")
                .build()
                .toUri(),
        )

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun hentDistribusjonInfo(journalpostId: String): DistribusjonInfoDto? {
        val url = UriComponentsBuilder.fromUri(url).path("/journal/distribuer/info/$journalpostId")
        return getForEntity(url.build().toUri())
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun distribuer(
        journalpostId: String,
        adresse: DistribuerTilAdresse? = null,
        ettersendingsoppgave: OpprettEttersendingsppgaveDto? = null,
        lokalUtskrift: Boolean = false,
        batchId: String? = null,
    ): DistribuerJournalpostResponse? {
        var url = UriComponentsBuilder.fromUri(url).path("/journal/distribuer/$journalpostId")
        if (batchId.isNotNullOrEmpty()) url = url.queryParam("batchId", batchId)
        return postForEntity(
            url.build().toUri(),
            DistribuerJournalpostRequest(adresse = adresse, lokalUtskrift = lokalUtskrift, ettersendingsoppgave = ettersendingsoppgave),
        )
    }
}
