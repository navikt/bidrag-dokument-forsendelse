package no.nav.bidrag.dokument.forsendelse.config

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
import no.nav.bidrag.commons.unleash.EnableUnleashFeatures
import no.nav.bidrag.commons.web.CorrelationIdFilter
import no.nav.bidrag.commons.web.DefaultCorsFilter
import no.nav.bidrag.commons.web.MdcFilter
import no.nav.bidrag.commons.web.UserMdcFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
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
}

@EnableUnleashFeatures
@Profile("nais")
@Configuration
class UnleashConfiguration
