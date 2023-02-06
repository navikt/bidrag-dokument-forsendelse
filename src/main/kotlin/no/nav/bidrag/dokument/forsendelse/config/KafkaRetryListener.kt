package no.nav.bidrag.dokument.forsendelse.config

import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.RetryListener

private val log = KotlinLogging.logger {}

class KafkaRetryListener : RetryListener {

    override fun failedDelivery(
        record: ConsumerRecord<*, *>,
        exception: Exception,
        deliveryAttempt: Int
    ) {
        log.warn(
            "Håndtering av kafka melding ${record.value()} feilet. Dette er $deliveryAttempt. forsøk",
            exception
        )
    }

    override fun recovered(record: ConsumerRecord<*, *>, exception: java.lang.Exception) {
        log.warn(
            "Håndtering av kafka melding ${record.value()} er enten suksess eller ignorert pågrunn av ugyldig data",
            exception
        )
    }

    override fun recoveryFailed(
        record: ConsumerRecord<*, *>,
        original: java.lang.Exception,
        failure: java.lang.Exception
    ) {
    }
}