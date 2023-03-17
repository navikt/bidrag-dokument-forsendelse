package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.dokument.dto.*
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
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
    @Qualifier("azure") private val restTemplate: RestOperations
) : AbstractRestClient(restTemplate, "bidrag-dokument") {

    private fun createUri(path: String?) = UriComponentsBuilder.fromUri(url)
        .path(path ?: "").build().toUri()

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun opprettJournalpost(opprettJournalpostRequest: OpprettJournalpostRequest): OpprettJournalpostResponse? {
        return postForEntity(createUri("/journalpost/JOARK"), opprettJournalpostRequest)
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun hentDokument(journalpostId: String, dokumentId: String?): ByteArray? {
        return getForEntity(
            UriComponentsBuilder.fromUri(url)
                .path("/dokument/$journalpostId/$dokumentId").queryParam("optimizeForPrint", "false")
                .build().toUri()
        )
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun hentDistribusjonInfo(journalpostId: String): DistribusjonInfoDto? {
        val url = UriComponentsBuilder.fromUri(url).path("/journal/distribuer/info/$journalpostId")
        return getForEntity(url.build().toUri())
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun distribuer(
        journalpostId: String,
        adresse: DistribuerTilAdresse? = null,
        lokalUtskrift: Boolean = false,
        batchId: String? = null
    ): DistribuerJournalpostResponse? {
        var url = UriComponentsBuilder.fromUri(url).path("/journal/distribuer/$journalpostId")
        if (batchId.isNotNullOrEmpty()) url = url.queryParam("batchId", batchId)
        return postForEntity(url.build().toUri(), DistribuerJournalpostRequest(adresse = adresse, lokalUtskrift = lokalUtskrift))
    }
}