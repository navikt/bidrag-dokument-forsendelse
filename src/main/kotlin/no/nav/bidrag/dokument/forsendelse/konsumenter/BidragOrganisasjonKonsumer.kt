package no.nav.bidrag.dokument.forsendelse.konsumenter

import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.dokument.forsendelse.konfigurasjon.CacheConfig.Companion.SAKSBEHANDLERINFO_CACHE
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.SaksbehandlerInfoResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class BidragOrganisasjonKonsumer(
    @Value("\${BIDRAG_ORGANISASJON_URL}") bidragOrgUrl: String,
    baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
): DefaultConsumer("bidrag-organisasjon", bidragOrgUrl, baseRestTemplate, securityTokenService) {

    @Cacheable(SAKSBEHANDLERINFO_CACHE)
    fun hentSaksbehandlerInfo(saksbehandlerIdent: String): SaksbehandlerInfoResponse? {
        return restTemplate.exchange(
            String.format(SAKSBEHANDLER_INFO, saksbehandlerIdent),
            HttpMethod.GET,
            null,
            SaksbehandlerInfoResponse::class.java
        ).body
    }

    companion object {
        const val SAKSBEHANDLER_INFO = "/saksbehandler/info/%s"
    }
}