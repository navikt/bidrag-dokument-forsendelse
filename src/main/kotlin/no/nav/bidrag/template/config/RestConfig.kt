package no.nav.bidrag.template.config

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.bidrag.commons.CorrelationId
import no.nav.bidrag.commons.security.api.EnableSecurityConfiguration
import no.nav.bidrag.commons.web.HttpHeaderRestTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.metrics.web.client.MetricsRestTemplateCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.web.client.RestTemplate


@Configuration
@EnableSecurityConfiguration
class RestConfig {
    @Bean
    @Scope("prototype")
    fun baseRestTemplate(@Value("\${NAIS_APP_NAME}") naisAppName: String, metricsRestTemplateCustomizer: MetricsRestTemplateCustomizer ): RestTemplate {
        val restTemplate = HttpHeaderRestTemplate()
        restTemplate.requestFactory = HttpComponentsClientHttpRequestFactory()
        restTemplate.withDefaultHeaders()
        restTemplate.addHeaderGenerator("Nav-Callid") { CorrelationId.fetchCorrelationIdForThread() }
        restTemplate.addHeaderGenerator("Nav-Consumer-Id") { naisAppName }
        metricsRestTemplateCustomizer.customize(restTemplate)
        return restTemplate
    }

    @Bean
    fun jackson2ObjectMapperBuilder(): Jackson2ObjectMapperBuilder {
        return Jackson2ObjectMapperBuilder().serializationInclusion(JsonInclude.Include.NON_NULL)
    }

}