package no.nav.bidrag.dokument.forsendelse.hendelse

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseHendelseBestilling
import no.nav.bidrag.dokument.forsendelse.persistence.database.repository.ForsendelseRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

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
