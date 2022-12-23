package no.nav.bidrag.dokument.forsendelse.hendelse

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.bidrag.dokument.dto.DokumentHendelse
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class DokumentKafkaHendelseProdusent(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${TOPIC_DOKUMENT}") val topic: String
) {
    fun publiser(dokumentHendelse: DokumentHendelse) {
        LOGGER.info("Publiserer Dokumenthendelse for dokumentreferanse {}", dokumentHendelse.dokumentreferanse)
        SIKKER_LOGG.info("Publiserer DokumentHendelse {}", dokumentHendelse)
        kafkaTemplate.send(topic, dokumentHendelse.dokumentreferanse, objectMapper.writeValueAsString(dokumentHendelse))
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DokumentKafkaHendelseProdusent::class.java)
    }
}