package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.TILGANG_SAK_CACHE
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.TILGANG_PERSON_CACHE
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
class BidragTIlgangskontrollConsumer(
    @Value("\${BIDRAG_TILGANGSKONTROLL_URL}") bidragTilgangskontroll: String,
    baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
): DefaultConsumer("bidrag-tilgangskontroll", bidragTilgangskontroll, baseRestTemplate, securityTokenService) {

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    @BrukerCacheable(TILGANG_SAK_CACHE)
    fun sjekkTilgangSak(saksnummer: String): Boolean {
        return try {
            restTemplate.exchange(
                "/api/tilgang/sak",
                HttpMethod.POST,
                HttpEntity(saksnummer),
                Boolean::class.java
            ).body ?: false
        } catch (e: HttpStatusCodeException){
            if (e.statusCode == HttpStatus.FORBIDDEN) return false
            if (e.statusCode == HttpStatus.NOT_FOUND) fantIkkeSak(saksnummer)
            throw e
        }
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    @BrukerCacheable(TILGANG_PERSON_CACHE)
    fun sjekkTilgangPerson(personnummer: String): Boolean {
        return try {
            restTemplate.exchange(
                "/api/tilgang/person",
                HttpMethod.POST,
                HttpEntity(personnummer),
                Boolean::class.java
            ).body ?: false
        } catch (e: HttpStatusCodeException){
            if (e.statusCode == HttpStatus.FORBIDDEN) return false
            throw e
        }
    }
}