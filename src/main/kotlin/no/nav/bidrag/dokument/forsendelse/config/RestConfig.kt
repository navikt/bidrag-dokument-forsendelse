package no.nav.bidrag.dokument.forsendelse.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.util.StdDateFormat
import jakarta.annotation.PostConstruct
import no.nav.bidrag.commons.security.api.EnableSecurityConfiguration
import no.nav.bidrag.commons.service.KodeverkProvider
import no.nav.bidrag.commons.web.config.RestOperationsAzure
import no.nav.bidrag.commons.web.interceptor.BearerTokenClientInterceptor
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.io.SocketConfig
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter
import org.springframework.web.client.RestTemplate

@Configuration
@EnableSecurityConfiguration
@Import(RestOperationsAzure::class)
class RestConfig(
    @Value("\${KODEVERK_URL}") kodeverkUrl: String,
) {
    init {
        KodeverkProvider.initialiser(kodeverkUrl)
    }

    @Bean
    fun clientRequestObservationConvention() = DefaultClientRequestObservationConvention()

    @Bean
    fun jackson2ObjectMapperBuilder(): Jackson2ObjectMapperBuilder =
        Jackson2ObjectMapperBuilder()
            .dateFormat(StdDateFormat())
            .failOnUnknownProperties(false)
            .serializationInclusion(JsonInclude.Include.NON_NULL)

    @Bean("azureLongerTimeout")
    @Scope("prototype")
    fun restOperationsJwtBearerNoBuffer(
        restTemplateBuilder: RestTemplateBuilder,
        bearerTokenClientInterceptor: BearerTokenClientInterceptor,
    ): RestTemplate {
        val restTemplate =
            restTemplateBuilder
                .requestFactoryBuilder {
                    val sc = SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(5)).build()
                    val pb = PoolingHttpClientConnectionManagerBuilder.create().setDefaultSocketConfig(sc).build()
                    val connectionManager = HttpClientBuilder.create().setConnectionManager(pb).build()
                    HttpComponentsClientHttpRequestFactory(connectionManager)
                }.additionalInterceptors(bearerTokenClientInterceptor)
                .build()
        configureJackson(restTemplate)
        return restTemplate
    }
}

fun configureJackson(restTemplate: RestTemplate) {
    restTemplate.messageConverters
        .stream()
        .filter { obj -> MappingJackson2HttpMessageConverter::class.java.isInstance(obj) }
        .map { obj -> MappingJackson2HttpMessageConverter::class.java.cast(obj) }
        .findFirst()
        .ifPresent { converter: MappingJackson2HttpMessageConverter ->
            converter.objectMapper = commonObjectmapper
        }

    restTemplate.messageConverters =
        restTemplate.messageConverters
            .filter { obj -> !MappingJackson2XmlHttpMessageConverter::class.java.isInstance(obj) }
            .toMutableList()
}

@Profile("nais")
@Configuration
class InitializeCaches(
    val bidragDokumentBestillingConsumer: BidragDokumentBestillingConsumer,
) {
    @PostConstruct
    fun initCache() {
        bidragDokumentBestillingConsumer.støttedeDokumentmaler()
        bidragDokumentBestillingConsumer.dokumentmalDetaljer()
        KodeverkProvider.initialiserKodeverkCache()
    }
}
