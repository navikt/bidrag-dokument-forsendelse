package no.nav.bidrag.template.consumer

import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.template.SECURE_LOGGER
import no.nav.bidrag.template.config.CacheConfig.Companion.PERSON_CACHE
import no.nav.bidrag.template.model.HentPersonResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class BidragPersonConsumer(
    @Value("\${BIDRAG_PERSON_URL}") bidragPersonUrl: String, baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
) :
    DefaultConsumer("bidrag-person", "$bidragPersonUrl/bidrag-person", baseRestTemplate, securityTokenService) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(BidragPersonConsumer::class.java)
    }

    @Cacheable(PERSON_CACHE)
    fun hentPerson(personId: String): HentPersonResponse? {
        SECURE_LOGGER.info("Henter person med id $personId")
        LOGGER.info("Henter person")
        val hentPersonResponse =
            restTemplate.exchange("/informasjon/$personId", HttpMethod.GET, null, HentPersonResponse::class.java)
        return hentPersonResponse.body
    }
}
