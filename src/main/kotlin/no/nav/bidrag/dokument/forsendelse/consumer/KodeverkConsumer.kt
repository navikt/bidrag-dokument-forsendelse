package no.nav.bidrag.dokument.forsendelse.consumer

import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import no.nav.bidrag.commons.CorrelationId
import no.nav.bidrag.commons.web.HttpHeaderRestTemplate
import no.nav.bidrag.dokument.forsendelse.database.model.KodeverkResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RootUriTemplateHandler
import org.springframework.cache.CacheManager
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

private val log = KotlinLogging.logger {}

@Service
class KodeverkConsumer(@Value("\${KODEVERK_URL}") kodeverkUrl: String, cacheManager: CacheManager) {
    private val restTemplate: RestTemplate
    private val cacheManager: CacheManager
    private final val KOMMUNER_CACHE = "KOMMUNER_CACHE"
    private final val POSTNUMMERE_CACHE = "POSTNUMMERE_CACHE"
    private final val DEFAULT_CACHE = "DEFAULT"

    init {
        this.cacheManager = cacheManager
        val restTemplate = HttpHeaderRestTemplate()
        restTemplate.uriTemplateHandler = RootUriTemplateHandler("$kodeverkUrl/api/v1/kodeverk")
        restTemplate.addHeaderGenerator("Nav-Call-Id") { CorrelationId.generateTimestamped("bidrag-dokument-forsendelse").get() }
        restTemplate.addHeaderGenerator("Nav-Consumer-Id") { "bidrag-dokument-forsendelse" }
        this.restTemplate = restTemplate
    }

    @PostConstruct
    fun preloadKodeverkValues() {
        loadKommuner()
        loadPostnummere()
    }

    private fun loadKommuner() {
        log.info("Henter kommuner fra kodeverk")
        val response = restTemplate.exchange("/Kommuner/koder/betydninger?spraak=nb", HttpMethod.GET, null, KodeverkResponse::class.java)
        val kommuner = response.body
        cacheManager.getCache(KOMMUNER_CACHE)?.put(DEFAULT_CACHE, kommuner)
    }

    private fun loadPostnummere() {
        log.info("Henter postnummere fra kodeverk")
        val response = restTemplate.exchange("/Postnummer/koder/betydninger?spraak=nb", HttpMethod.GET, null, KodeverkResponse::class.java)
        val postnummere = response.body
        cacheManager.getCache(POSTNUMMERE_CACHE)?.put(DEFAULT_CACHE, postnummere)
    }

    fun hentKommuner(): KodeverkResponse {
        return cacheManager.getCache(KOMMUNER_CACHE)!!.get(DEFAULT_CACHE, KodeverkResponse::class.java)!!
    }

    fun hentPostnummre(): KodeverkResponse {
        return cacheManager.getCache(POSTNUMMERE_CACHE)!!.get(DEFAULT_CACHE, KodeverkResponse::class.java)!!
    }
}
