package no.nav.bidrag.dokument.forsendelse.hendelse

import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseHendelseBestilling
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import javax.transaction.Transactional

private val LOGGER = KotlinLogging.logger {}

@Component
class ForsendelseLytter(
    private val forsendelseRepository: ForsendelseRepository,
    private val journalpostKafkaHendelseProdusent: JournalpostKafkaHendelseProdusent,
) {


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun sendHendelse(bestilling: ForsendelseHendelseBestilling) {
        val forsendelseId = bestilling.forsendelseId
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId)
        forsendelse?.let { journalpostKafkaHendelseProdusent.publiserForsendelse(it) }
            ?: LOGGER.error { "Fant ikke forsendelse $forsendelseId. Kunne ikke publisere hendelse" }
    }
}