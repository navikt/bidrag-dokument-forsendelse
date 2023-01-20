package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingKonsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class DokumentBestillingService(val applicationEventPublisher: ApplicationEventPublisher, val dokumentBestillingKonsumer: BidragDokumentBestillingKonsumer) {


    fun bestill(forsendelseId: Long, dokumentreferanse: String) {
        applicationEventPublisher.publishEvent(DokumentBestilling(forsendelseId, dokumentreferanse))
    }

    fun hentDokumentmalDetaljer(): Map<String, DokumentMalDetaljer> {
        return dokumentBestillingKonsumer.dokumentmalDetaljer()
    }
}