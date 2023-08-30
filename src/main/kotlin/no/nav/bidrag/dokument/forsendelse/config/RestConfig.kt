package no.nav.bidrag.dokument.forsendelse.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.util.StdDateFormat
import no.nav.bidrag.commons.security.api.EnableSecurityConfiguration
import no.nav.bidrag.commons.web.config.RestOperationsAzure
import no.nav.bidrag.commons.web.interceptor.BearerTokenClientInterceptor
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder


@Configuration
@EnableSecurityConfiguration
@Import(RestOperationsAzure::class)
class RestConfig {

    @Bean
    fun jackson2ObjectMapperBuilder(): Jackson2ObjectMapperBuilder {
        return Jackson2ObjectMapperBuilder()
            .dateFormat(StdDateFormat())
            .failOnUnknownProperties(false)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
    }

    @Bean("azureNotBuffer")
    @Scope("prototype")
    fun restOperationsJwtBearerNoBuffer(
        restTemplateBuilder: RestTemplateBuilder,
        bearerTokenClientInterceptor: BearerTokenClientInterceptor
    ) = restTemplateBuilder
        .requestFactory { _ ->
            val reqFact = SimpleClientHttpRequestFactory()
            reqFact.setBufferRequestBody(false)
            reqFact
        }
        .additionalInterceptors(bearerTokenClientInterceptor)
        .build()

}
