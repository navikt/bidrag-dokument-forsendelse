package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig
import no.nav.bidrag.dokument.forsendelse.model.hentSakFeilet
import no.nav.bidrag.transport.sak.BidragssakDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class BidragSakConsumer(
    @Value("\${BIDRAG_SAK_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-sak") {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BidragSakConsumer::class.java)
    }

    private fun createUri(path: String?) =
        UriComponentsBuilder.fromUri(url)
            .path(path ?: "").build().toUri()

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    @Cacheable(CacheConfig.SAK_CACHE)
    fun hentSak(saksnr: String): BidragssakDto? {
        try {
            return getForEntity(createUri("/sak/$saksnr"))
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                return null
            }
            hentSakFeilet(saksnr)
        }
    }
}
