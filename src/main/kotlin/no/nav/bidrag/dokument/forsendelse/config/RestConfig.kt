package no.nav.bidrag.dokument.forsendelse.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jakarta.annotation.PostConstruct
import no.nav.bidrag.commons.security.api.EnableSecurityConfiguration
import no.nav.bidrag.commons.service.KodeverkProvider
import no.nav.bidrag.commons.web.config.RestOperationsAzure
import no.nav.bidrag.commons.web.config.RestOperationsAzure.CustomJacksonHttpMessageConverter
import no.nav.bidrag.commons.web.interceptor.BearerTokenClientInterceptor
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.io.SocketConfig
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention
import org.springframework.http.converter.AbstractGenericHttpMessageConverter
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter
import org.springframework.http.converter.xml.MarshallingHttpMessageConverter
import org.springframework.web.client.RestTemplate
import java.lang.reflect.Type

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
    fun objectMapper(): ObjectMapper =
        ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .setDateFormat(
                tools.jackson.databind.util
                    .StdDateFormat(),
            ).setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

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
        return restTemplate
    }
}

/**
 * Creates a list of message converters with the common ObjectMapper.
 * This ensures all REST calls use consistent Jackson configuration.
 */
private fun createMessageConverters(): List<HttpMessageConverter<*>> =
    listOf(
        ByteArrayHttpMessageConverter(),
        StringHttpMessageConverter(),
        CustomJacksonHttpMessageConverter(commonObjectmapper),
        Jaxb2RootElementHttpMessageConverter(),
        MarshallingHttpMessageConverter(),
        FormHttpMessageConverter(),
    )

/**
 * Custom JSON message converter that uses the shared ObjectMapper configuration.
 * This avoids ClassLoader/version conflicts by ensuring all deserialization
 * uses the same ObjectMapper instance.
 */
private class CustomJacksonHttpMessageConverter(
    private val objectMapper: ObjectMapper,
) : AbstractGenericHttpMessageConverter<Any>(
        MediaType.APPLICATION_JSON,
        MediaType("application", "*+json"),
    ) {
    override fun supports(clazz: Class<*>): Boolean = true

    override fun read(
        type: Type,
        contextClass: Class<*>?,
        inputMessage: HttpInputMessage,
    ): Any = objectMapper.readValue(inputMessage.body, objectMapper.constructType(type))

    override fun readInternal(
        clazz: Class<out Any>,
        inputMessage: HttpInputMessage,
    ): Any = objectMapper.readValue(inputMessage.body, clazz)

    override fun writeInternal(
        obj: Any,
        type: Type?,
        outputMessage: HttpOutputMessage,
    ) {
        outputMessage.body.use { os ->
            if (type == null) {
                objectMapper.writeValue(os, obj)
            } else {
                objectMapper.writerFor(objectMapper.constructType(type)).writeValue(os, obj)
            }
        }
    }
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
