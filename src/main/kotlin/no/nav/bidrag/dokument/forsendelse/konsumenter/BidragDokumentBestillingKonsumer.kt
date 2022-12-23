package no.nav.bidrag.dokument.forsendelse.konsumenter

import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.konfigurasjon.CacheConfig.Companion.DOKUMENTMALER_CACHE
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentBestillingForespørsel
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentBestillingResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
@Service
class BidragDokumentBestillingKonsumer(
    @Value("\${BIDRAG_DOKUMENT_BESTILLING_URL}") bidragDokumentBestillingUrl: String, baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
) :
    DefaultConsumer("bidrag-dokument-bestilling", bidragDokumentBestillingUrl, baseRestTemplate, securityTokenService) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(BidragDokumentBestillingKonsumer::class.java)
    }

    fun bestill(forespørsel: DokumentBestillingForespørsel, dokumentmalId: String): DokumentBestillingResponse? {
        val respons = restTemplate.exchange("/bestill/$dokumentmalId", HttpMethod.POST, HttpEntity(forespørsel), DokumentBestillingResponse::class.java).body
        LOGGER.info("Bestilte dokument med dokumentmalId $dokumentmalId")
        SIKKER_LOGG.info("Bestilte dokument med dokumentmalId $dokumentmalId og forespørsel $forespørsel")
        return respons
    }

    @Cacheable(DOKUMENTMALER_CACHE)
    fun støttedeDokumentmaler(): List<String> {
        return restTemplate.exchange("/brevkoder", HttpMethod.OPTIONS, null, typeReference<List<String>>()).body ?: emptyList()
    }
}
