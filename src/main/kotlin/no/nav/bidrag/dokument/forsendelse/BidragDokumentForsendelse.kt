package no.nav.bidrag.dokument.forsendelse

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration

const val PROFILE_NAIS = "nais"
val SIKKER_LOGG = KotlinLogging.logger("secureLogger")

@SpringBootApplication(
    exclude = [
        SecurityAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
        ServletWebSecurityAutoConfiguration::class,
    ],
)
@EnableJwtTokenValidation(ignore = ["org.springframework", "org.springdoc"])
class BidragTemplateSpring

fun main(args: Array<String>) {
    val app = SpringApplication(BidragTemplateSpring::class.java)
    app.setAdditionalProfiles(if (args.isEmpty()) PROFILE_NAIS else args[0])
    app.run(*args)
}
