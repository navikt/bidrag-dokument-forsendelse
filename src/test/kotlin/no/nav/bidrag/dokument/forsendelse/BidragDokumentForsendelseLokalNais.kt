package no.nav.bidrag.dokument.forsendelse

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.SpringApplication
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.context.annotation.EnableAspectJAutoProxy

@SpringBootApplication(exclude = [SecurityAutoConfiguration::class, ManagementWebSecurityAutoConfiguration::class])
@EnableAspectJAutoProxy
@EnableJwtTokenValidation(ignore = ["org.springframework", "org.springdoc"])
class BidragDokumentForsendelseLokalNais

fun main(args: Array<String>) {
    val app = SpringApplication(BidragDokumentForsendelseLokalNais::class.java)
    app.setAdditionalProfiles("lokal-nais","lokal-nais-secrets", "lokal", "nais")
    app.run(*args)
}
