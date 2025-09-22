package no.nav.bidrag.dokument.forsendelse.consumer

import no.nav.bidrag.commons.web.client.AbstractRestClient
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.DOKUMENTMALDETALJER_CACHE
import no.nav.bidrag.dokument.forsendelse.config.CacheConfig.Companion.DOKUMENTMALER_CACHE
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentBestillingForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentBestillingResponse
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.ParameterizedTypeReference
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}

@Service
class BidragDokumentBestillingConsumer(
    @Value("\${BIDRAG_DOKUMENT_BESTILLING_URL}") val url: URI,
    @Qualifier("azure") private val restTemplate: RestOperations,
) : AbstractRestClient(restTemplate, "bidrag-dokument-bestilling") {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BidragDokumentBestillingConsumer::class.java)
    }

    private fun createUri(path: String?) =
        UriComponentsBuilder
            .fromUri(url)
            .path(path ?: "")
            .build()
            .toUri()

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun bestill(
        forespørsel: DokumentBestillingForespørsel,
        dokumentmalId: String,
    ): DokumentBestillingResponse? {
        val respons: DokumentBestillingResponse? = postForEntity(createUri("/bestill/$dokumentmalId"), forespørsel)
        SIKKER_LOGG.debug("Bestilte dokument med dokumentmalId $dokumentmalId og forespørsel $forespørsel")
        return respons
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun hentDokument(
        dokumentmalId: String,
        forespørsel: DokumentBestillingForespørsel? = null,
    ): ByteArray? {
        LOGGER.info("Henter dokument med dokumentmalId $dokumentmalId")
        return postForEntity(createUri("/dokument/$dokumentmalId"), forespørsel)
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 200, maxDelay = 1000, multiplier = 2.0))
    fun produser(
        dokumentmalId: String,
        forespørsel: DokumentBestillingForespørsel? = null,
    ): ByteArray? {
        LOGGER.info("Henter dokument med dokumentmalId $dokumentmalId")
        return postForEntity(createUri("/produser/$dokumentmalId"), forespørsel)
    }

    @Cacheable(DOKUMENTMALER_CACHE)
    fun støttedeDokumentmaler(): List<String> =
        optionsForEntity(createUri("/brevkoder"))
            ?: emptyList()

    @Cacheable(DOKUMENTMALDETALJER_CACHE)
    fun dokumentmalDetaljer(): Map<String, DokumentMalDetaljer> = getForEntity(createUri("/dokumentmal/detaljer")) ?: emptyMap()
}
