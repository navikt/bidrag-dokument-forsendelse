package no.nav.bidrag.dokument.forsendelse.konfigurasjon

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import no.nav.bidrag.commons.web.CorrelationIdFilter
import no.nav.bidrag.commons.web.DefaultCorsFilter
import no.nav.bidrag.commons.web.UserMdcFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.retry.annotation.EnableRetry


@EnableAspectJAutoProxy
@OpenAPIDefinition(info = Info(title = "bidrag-dokument-forsendelse", version = "v1"), security = [SecurityRequirement(name = "bearer-key")])
@SecurityScheme(bearerFormat = "JWT", name = "bearer-key", scheme = "bearer", type = SecuritySchemeType.HTTP)
@EnableRetry
class DefaultConfiguration {

    @Bean
    fun correlationIdFilter() = CorrelationIdFilter()

    @Bean
    fun corsFilter() = DefaultCorsFilter()

    @Bean
    fun mdcFilter() = UserMdcFilter()
}