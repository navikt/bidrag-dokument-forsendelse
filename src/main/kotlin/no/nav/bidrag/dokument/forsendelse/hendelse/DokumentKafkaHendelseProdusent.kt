package no.nav.bidrag.dokument.forsendelse.hendelse

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.transport.dokument.DokumentHendelse
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class DokumentKafkaHendelseProdusent(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${TOPIC_DOKUMENT}") val topic: String,
) {
    @Retryable(value = [Exception::class], maxAttempts = 10, backoff = Backoff(delay = 1000, maxDelay = 12000, multiplier = 2.0))
    fun publiser(dokumentHendelse: DokumentHendelse) {
        log.info("Publiserer Dokumenthendelse for dokumentreferanse ${dokumentHendelse.dokumentreferanse}")
        SIKKER_LOGG.info("Publiserer DokumentHendelse $dokumentHendelse")
        kafkaTemplate.send(topic, dokumentHendelse.dokumentreferanse, objectMapper.writeValueAsString(dokumentHendelse))
    }
}
