package no.nav.bidrag.dokument.forsendelse.config

import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.model.KunneIkkeLeseMeldingFraHendelse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.util.backoff.ExponentialBackOff

private val log = KotlinLogging.logger {}

@Configuration
class KafkaKonfig {

    @Bean
    fun defaultErrorHandler(@Value("\${KAFKA_MAX_RETRY:-1}") maxRetry: Int): DefaultErrorHandler {
        // Max retry should not be set in production
        val backoffPolicy =
            if (maxRetry == -1) ExponentialBackOff() else ExponentialBackOffWithMaxRetries(maxRetry)
        backoffPolicy.multiplier = 2.0
        backoffPolicy.maxInterval = 1800000L // 30 mins
        log.info(
            "Initializing Kafka errorhandler with backoffpolicy {}, maxRetry={}",
            backoffPolicy,
            maxRetry
        )
        val errorHandler = DefaultErrorHandler({ rec, e ->
            val key = rec.key()
            val value = rec.value()
            val offset = rec.offset()
            val topic = rec.topic()
            val partition = rec.partition()
            SIKKER_LOGG.error(
                "Kafka melding med nøkkel $key, partition $partition og topic $topic feilet på offset $offset. Melding som feilet: $value",
                e
            )
        }, backoffPolicy)
        errorHandler.setRetryListeners(KafkaRetryListener())
        errorHandler.addNotRetryableExceptions(KunneIkkeLeseMeldingFraHendelse::class.java)
        return errorHandler
    }
}