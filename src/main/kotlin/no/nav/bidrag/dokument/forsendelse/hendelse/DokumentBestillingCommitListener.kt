package no.nav.bidrag.dokument.forsendelse.hendelse

import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import no.nav.bidrag.dokument.forsendelse.service.DokumentBestillingService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DokumentBestillingCommitListener(
    val dokumentBestillingService: DokumentBestillingService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun bestill(bestilling: DokumentBestilling) {
        if (!bestilling.waitForCommit) return
        dokumentBestillingService.bestillAsync(bestilling.copy(waitForCommit = false))
    }
}
