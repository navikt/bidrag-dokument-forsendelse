package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragDokumentBestillingKonsumer
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentBestillingForespørsel
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentBestillingResponse
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import javax.transaction.Transactional

@Component
class DokumentBestillingTjeneste(val applicationEventPublisher: ApplicationEventPublisher) {


    fun bestill(forsendelseId: Long, dokumentreferanse: String) {
        applicationEventPublisher.publishEvent(DokumentBestilling(forsendelseId, dokumentreferanse))
    }
}