package no.nav.bidrag.dokument.forsendelse.konsumenter

import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DistribuerTilAdresse
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class BidragDokumentKonsumer(
    @Value("\${BIDRAG_DOKUMENT_URL}") bidragDokument: String,
    baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
): DefaultConsumer("bidrag-dokument", bidragDokument, baseRestTemplate, securityTokenService) {

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun opprettJournalpost(opprettJournalpostRequest: OpprettJournalpostRequest): OpprettJournalpostResponse? {
        return restTemplate.exchange(
            "/journalpost/JOARK",
            HttpMethod.POST,
            HttpEntity(opprettJournalpostRequest),
            OpprettJournalpostResponse::class.java
        ).body
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun hentDokument(journalpostId: String, dokumentId: String?): ByteArray? {
        return restTemplate.exchange(
            "/dokument/$journalpostId/$dokumentId?optimizeForPrint=false",
            HttpMethod.GET,
            null,
            ByteArray::class.java
        ).body
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun distribuer(journalpostId: String, adresse: DistribuerTilAdresse? = null, lokalUtskrift: Boolean = false): DistribuerJournalpostResponse? {
        return restTemplate.exchange(
            "/journal/distribuer/$journalpostId",
            HttpMethod.POST,
            HttpEntity(DistribuerJournalpostRequest(adresse = adresse, lokalUtskrift = lokalUtskrift)),
            DistribuerJournalpostResponse::class.java
        ).body
    }
}