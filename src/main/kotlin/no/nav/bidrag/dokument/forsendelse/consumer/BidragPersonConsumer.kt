package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.PERSON_CACHE
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.PERSON_SPRAAK_CACHE
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.SAKSBEHANDLERINFO_CACHE
import no.nav.bidrag.dokument.forsendelse.consumer.dto.HentPersonInfoRequest
import no.nav.bidrag.dokument.forsendelse.consumer.dto.HentPersonResponse
import no.nav.bidrag.dokument.forsendelse.consumer.dto.SaksbehandlerInfoResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Service
class BidragPersonConsumer(
    @Value("\${BIDRAG_PERSON_URL}") bidragPersonUrl: String,
    baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
): DefaultConsumer("bidrag-person", bidragPersonUrl, baseRestTemplate, securityTokenService) {

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    @BrukerCacheable(PERSON_CACHE)
    fun hentPerson(personId: String): HentPersonResponse? {
        return try {
            restTemplate.exchange("/informasjon", HttpMethod.POST, HttpEntity(HentPersonInfoRequest(personId)), HentPersonResponse::class.java).body
        } catch (e: HttpStatusCodeException){
            if (e.statusCode == HttpStatus.NOT_FOUND){
                return null
            }
            throw e
        }
    }

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    @BrukerCacheable(PERSON_SPRAAK_CACHE)
    fun hentPersonSpråk(personId: String): String? {
        return try {
            restTemplate.exchange("/spraak", HttpMethod.POST, HttpEntity(HentPersonInfoRequest(personId)), String::class.java).body
        } catch (e: HttpStatusCodeException){
            if (e.statusCode == HttpStatus.NOT_FOUND){
                return null
            }
            throw e
        }
    }
}