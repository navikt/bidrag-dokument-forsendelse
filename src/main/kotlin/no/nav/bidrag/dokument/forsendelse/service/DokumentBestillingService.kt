package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class DokumentBestillingService(
    val applicationEventPublisher: ApplicationEventPublisher,
    val dokumentBestillingKonsumer: BidragDokumentBestillingConsumer,
) {
    fun bestill(
        forsendelseId: Long,
        dokumentreferanse: String,
    ) {
        applicationEventPublisher.publishEvent(DokumentBestilling(forsendelseId, dokumentreferanse))
    }

    @Async
    fun bestillAsync(
        forsendelseId: Long,
        dokumentreferanse: String,
    ) {
        applicationEventPublisher.publishEvent(DokumentBestilling(forsendelseId, dokumentreferanse))
    }

    fun hentDokumentmalDetaljer(): Map<String, DokumentMalDetaljer> = dokumentBestillingKonsumer.dokumentmalDetaljer()
}
