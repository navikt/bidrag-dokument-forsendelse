package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.model.ForsendelseHendelseBestilling
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ForsendelseHendelseBestillingService(
    val applicationEventPublisher: ApplicationEventPublisher
) {

    fun bestill(forsendelseId: Long) {
        applicationEventPublisher.publishEvent(ForsendelseHendelseBestilling(forsendelseId))
    }
}
