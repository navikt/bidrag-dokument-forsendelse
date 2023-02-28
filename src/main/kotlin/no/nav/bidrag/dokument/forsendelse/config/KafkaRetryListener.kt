package no.nav.bidrag.dokument.forsendelse.config

import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.RetryListener

class KafkaRetryListener : RetryListener {

    override fun failedDelivery(
        record: ConsumerRecord<*, *>,
        exception: Exception,
        deliveryAttempt: Int
    ) {
        SIKKER_LOGG.error(exception) { "Håndtering av kafka melding ${record.value()} feilet. Dette er $deliveryAttempt. forsøk" }
    }

    override fun recovered(record: ConsumerRecord<*, *>, exception: java.lang.Exception) {
        SIKKER_LOGG.error(exception) { "Håndtering av kafka melding ${record.value()} er enten suksess eller ignorert pågrunn av ugyldig data" }
    }

    override fun recoveryFailed(
        record: ConsumerRecord<*, *>,
        original: java.lang.Exception,
        failure: java.lang.Exception
    ) {
    }
}