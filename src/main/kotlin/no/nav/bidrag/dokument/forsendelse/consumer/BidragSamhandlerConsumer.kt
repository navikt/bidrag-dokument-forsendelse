package no.nav.bidrag.dokument.forsendelse.consumer

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.domene.ident.Ident
import no.nav.bidrag.transport.samhandler.SamhandlerDto
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

private val LOGGER = KotlinLogging.logger {}

@Service
class BidragSamhandlerConsumer(
    @Value("\${BIDRAG_SAMHANDLER_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-samhandler") {
    private fun createUri(path: String?) =
        UriComponentsBuilder
            .fromUri(url)
            .path(path ?: "")
            .build()
            .toUri()

    //    @Cacheable(SAMHANDLER_CACHE)
    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    fun hentSamhandler(samhandlerId: String): SamhandlerDto? {
        try {
            return postForNonNullEntity(createUri("/samhandler"), Ident(samhandlerId))
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode.value() == HttpStatus.NOT_FOUND.value() || e.statusCode.value() == HttpStatus.NO_CONTENT.value()) {
                LOGGER.warn(e) { "Fant ikke samhandler med id $samhandlerId" }
                return null
            }
            LOGGER.warn(e) { "Det skjedde en feil ved henting av samhandler $samhandlerId" }
            throw e
        }
    }
}
