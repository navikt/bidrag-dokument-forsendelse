package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.PERSON_CACHE
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.PERSON_SPRAAK_CACHE
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.person.PersonDto
import no.nav.bidrag.transport.person.PersonRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragPersonConsumer(
    @Value("\${BIDRAG_PERSON_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-person") {
    private fun createUri(path: String?) =
        UriComponentsBuilder
            .fromUri(url)
            .path(path ?: "")
            .build()
            .toUri()

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    @BrukerCacheable(PERSON_CACHE)
    fun hentPerson(personId: String): PersonDto? {
        return try {
            postForEntity(createUri("/informasjon"), PersonRequest(Personident(personId)))
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                return null
            }
            throw e
        }
    }

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    @BrukerCacheable(PERSON_SPRAAK_CACHE)
    fun hentPersonSpråk(personId: String): String? {
        return try {
            postForEntity(createUri("/spraak"), PersonRequest(Personident(personId)))
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                return null
            }
            throw e
        }
    }
}
