package no.nav.bidrag.dokument.forsendelse.konsumenter

import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.konfigurasjon.CacheConfig.Companion.PERSON_CACHE
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.HentPersonResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class BidragPersonKonsumer(
    @Value("\${BIDRAG_PERSON_URL}") bidragPersonUrl: String, baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
) :
    DefaultConsumer("bidrag-person", "$bidragPersonUrl/bidrag-person", baseRestTemplate, securityTokenService) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(BidragPersonKonsumer::class.java)
    }

    @Cacheable(PERSON_CACHE)
    fun hentPerson(personId: String): HentPersonResponse? {
        SIKKER_LOGG.info("Henter person med id $personId")
        LOGGER.info("Henter person")
        val hentPersonResponse =
            restTemplate.exchange("/informasjon/$personId", HttpMethod.GET, null, HentPersonResponse::class.java)
        return hentPersonResponse.body
    }
}
