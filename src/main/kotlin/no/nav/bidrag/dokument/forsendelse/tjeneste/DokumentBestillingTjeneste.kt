package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class DokumentBestillingTjeneste(val applicationEventPublisher: ApplicationEventPublisher) {


    fun bestill(forsendelseId: Long, dokumentreferanse: String) {
        applicationEventPublisher.publishEvent(DokumentBestilling(forsendelseId, dokumentreferanse))
    }
}