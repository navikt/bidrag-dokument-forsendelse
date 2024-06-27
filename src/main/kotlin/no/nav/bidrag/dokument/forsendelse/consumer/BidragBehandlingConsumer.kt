package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig
import no.nav.bidrag.dokument.forsendelse.consumer.dto.BehandlingDto
import no.nav.bidrag.dokument.forsendelse.model.HentVedtakFeiletException
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
class BidragBehandlingConsumer(
    @Value("\${BIDRAG_BEHANDLING_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
    @Value("\${HENT_DOKUMENTVALG_DETALJER_FRA_VEDTAK_BEHANDLING_ENABLED:false}") val hentDetaljerFraVedtakBehandlingEnabled: Boolean,
) : AbstractRestClient(restTemplate, "bidrag-behandling") {
    private fun createUri(path: String?) =
        UriComponentsBuilder
            .fromUri(url)
            .path(path ?: "")
            .build()
            .toUri()

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    @BrukerCacheable(CacheConfig.BEHANDLING_CACHE)
    fun hentBehandling(behandlingId: String): BehandlingDto? {
        if (!hentDetaljerFraVedtakBehandlingEnabled) return null
        try {
            return getForEntity(createUri("/api/v2/behandling/detaljer/$behandlingId"))
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                return null
            }
            throw HentVedtakFeiletException("Henting av behandling $behandlingId feilet", e)
        }
    }
}
