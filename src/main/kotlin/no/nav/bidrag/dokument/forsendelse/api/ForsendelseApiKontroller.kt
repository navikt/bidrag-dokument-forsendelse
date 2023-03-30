package no.nav.bidrag.dokument.forsendelse.api

import no.nav.security.token.support.core.api.Protected
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.annotation.Inherited

@MustBeDocumented
@Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@RestController
@Protected
@RequestMapping("/api/forsendelse")
annotation class ForsendelseApiKontroller
