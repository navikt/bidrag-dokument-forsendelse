package no.nav.bidrag.dokument.forsendelse.config

import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.RetryListener
import java.lang.Exception

class KafkaRetryListener : RetryListener {
    override fun failedDelivery(
        record: ConsumerRecord<*, *>,
        ex: Exception?,
        deliveryAttempt: Int,
    ) {
        SIKKER_LOGG.error(ex) { "Håndtering av kafka melding ${record.value()} feilet. Dette er $deliveryAttempt. forsøk" }
    }

    override fun recovered(
        record: ConsumerRecord<*, *>,
        ex: Exception?,
    ) {
        SIKKER_LOGG.error(
            ex,
        ) { "Håndtering av kafka melding ${record.value()} er enten suksess eller ignorert pågrunn av ugyldig data" }
    }

    override fun recoveryFailed(
        record: ConsumerRecord<*, *>,
        original: Exception?,
        failure: Exception,
    ) {
    }
}
