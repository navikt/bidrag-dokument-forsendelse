package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.cache.BrukerCacheable
import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig
import no.nav.bidrag.dokument.forsendelse.model.HentVedtakFeiletException
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
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
class BidragVedtakConsumer(
    @Value("\${BIDRAG_VEDTAK_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
    @Value("\${HENT_DOKUMENTVALG_DETALJER_FRA_VEDTAK_BEHANDLING_ENABLED:false}") val hentDetaljerFraVedtakBehandlingEnabled: Boolean
) : AbstractRestClient(restTemplate, "bidrag-vedtak") {

    private fun createUri(path: String?) = UriComponentsBuilder.fromUri(url)
        .path(path ?: "").build().toUri()

    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 500, maxDelay = 1500, multiplier = 2.0))
    @BrukerCacheable(CacheConfig.VEDTAK_CACHE)
    fun hentVedtak(vedtakId: String): VedtakDto? {
        if (hentDetaljerFraVedtakBehandlingEnabled) return null
        try {
            return getForEntity(createUri("/vedtak/$vedtakId"))
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                return null
            }
            throw HentVedtakFeiletException("Henting av vedtak $vedtakId feilet", e)
        }
    }
}
