package no.nav.bidrag.dokument.forsendelse.config

import io.getunleash.DefaultUnleash
import io.getunleash.UnleashContext
import io.getunleash.UnleashContextProvider
import io.getunleash.util.UnleashConfig
import io.micrometer.core.aop.TimedAspect
import io.micrometer.core.instrument.MeterRegistry
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import no.nav.bidrag.commons.service.organisasjon.EnableSaksbehandlernavnProvider
import no.nav.bidrag.commons.web.CorrelationIdFilter
import no.nav.bidrag.commons.web.DefaultCorsFilter
import no.nav.bidrag.commons.web.MdcFilter
import no.nav.bidrag.commons.web.UserMdcFilter
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling
import javax.sql.DataSource

@EnableAspectJAutoProxy
@OpenAPIDefinition(
    info = Info(title = "bidrag-dokument-forsendelse", version = "v1"),
    security = [SecurityRequirement(name = "bearer-key")],
)
@SecurityScheme(bearerFormat = "JWT", name = "bearer-key", scheme = "bearer", type = SecuritySchemeType.HTTP)
@EnableRetry
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
@Import(CorrelationIdFilter::class, DefaultCorsFilter::class, UserMdcFilter::class, MdcFilter::class)
@EnableSaksbehandlernavnProvider
class DefaultConfiguration {
    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration
                .builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()
                .build(),
        )

    @Bean
    fun timedAspect(registry: MeterRegistry): TimedAspect = TimedAspect(registry)

    @Bean
    fun unleashConfig(
        @Value("\${NAIS_APP_NAME}") appName: String,
        @Value("\${UNLEASH_SERVER_API_URL}") apiUrl: String,
        @Value("\${UNLEASH_SERVER_API_TOKEN}") apiToken: String,
        @Value("\${UNLEASH_SERVER_API_ENV}") environment: String,
    ) = UnleashConfig
        .builder()
        .appName(appName)
        .unleashAPI("$apiUrl/api/")
        .instanceId(appName)
        .environment(environment)
        .synchronousFetchOnInitialisation(true)
        .apiKey(apiToken)
        .unleashContextProvider(DefaultUnleashContextProvider())
        .build()

    @Bean
    @Scope("prototype")
    fun unleashInstance(unleashConfig: UnleashConfig) = DefaultUnleash(unleashConfig)
}

class DefaultUnleashContextProvider : UnleashContextProvider {
    override fun getContext(): UnleashContext {
        val userId = MDC.get("user")
        return UnleashContext
            .builder()
            .userId(userId)
            .appName(MDC.get("applicationKey"))
            .build()
    }
}
