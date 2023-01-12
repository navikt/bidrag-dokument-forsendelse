package no.nav.bidrag.dokument.forsendelse.konsumenter

import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DistribuerTilAdresse
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.konfigurasjon.CacheConfig.Companion.SAK_CACHE
import no.nav.bidrag.dokument.forsendelse.konfigurasjon.CacheConfig.Companion.SAK_PERSON_CACHE
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeSak
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Service
class BidragSakKonsumer(
    @Value("\${BIDRAG_SAK_URL}") bidragDokument: String,
    baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
): DefaultConsumer("bidrag-sak", bidragDokument, baseRestTemplate, securityTokenService) {

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    @BrukerCacheable(SAK_CACHE)
    fun sjekkTilgangSak(saksnummer: String): Boolean {
        return try {
            restTemplate.exchange(
                "/sak/$saksnummer",
                HttpMethod.GET,
                null,
                Void::class.java
            ).body
            true
        } catch (e: HttpStatusCodeException){
            if (e.statusCode == HttpStatus.FORBIDDEN) return false
            if (e.statusCode == HttpStatus.NOT_FOUND) fantIkkeSak(saksnummer)
            throw e
        }
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    @BrukerCacheable(SAK_PERSON_CACHE)
    fun sjekkTilgangPerson(personnummer: String): Boolean {
        return try {
            restTemplate.exchange(
                "/person/sak/$personnummer",
                HttpMethod.GET,
                null,
                Void::class.java
            ).body
            true
        } catch (e: HttpStatusCodeException){
            if (e.statusCode == HttpStatus.FORBIDDEN) return false
            throw e
        }
    }
}