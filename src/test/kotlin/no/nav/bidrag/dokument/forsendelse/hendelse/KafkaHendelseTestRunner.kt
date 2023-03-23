package no.nav.bidrag.dokument.forsendelse.hendelse

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldNotBe
import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DokumentHendelse
import no.nav.bidrag.dokument.dto.JournalpostHendelse
import no.nav.bidrag.dokument.forsendelse.CommonTestRunner
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.Assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils

private val log = KotlinLogging.logger {}

@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers", topics = ["bidrag.dokument", "bidrag.journalpost"])
abstract class KafkaHendelseTestRunner : CommonTestRunner() {
    @Value("\${TOPIC_DOKUMENT}")
    private lateinit var topicDokument: String

    @Value("\${TOPIC_JOURNALPOST}")
    private lateinit var topicJournalpost: String

    @BeforeEach
    fun setupMocks() {
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
    }

    @AfterEach
    fun cleanupDatabase() {
        testDataManager.slettAlleData()
    }

    @Autowired
    lateinit var embeddedKafkaBroker: EmbeddedKafkaBroker

    fun readFromJournalpostTopic(journalpostId: String? = null): JournalpostHendelse? {
        val consumer = configureConsumer(topicJournalpost)
        return try {
            val result = KafkaTestUtils.getRecords(consumer, 4000)
            result shouldNotBe null
            val records = result.records(topicJournalpost)
            val hendelser = records
                .map { ObjectMapper().findAndRegisterModules().readValue(it.value(), JournalpostHendelse::class.java) }

            return if (journalpostId.isNullOrEmpty()) hendelser.first()
            else hendelser.find { it.journalpostId == journalpostId }
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved lesing av kafka melding", e)
            null
        } finally {
            consumer?.close()
        }

    }

    fun readAllFromJournalpostTopic(): List<JournalpostHendelse> {
        val consumer = configureConsumer(topicJournalpost)
        return try {
            val result = KafkaTestUtils.getRecords(consumer, 4000)
            result shouldNotBe null
            val records = result.records(topicJournalpost)
            return records.map { ObjectMapper().findAndRegisterModules().readValue(it.value(), JournalpostHendelse::class.java) }
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved lesing av kafka melding", e)
            emptyList()
        } finally {
            consumer?.close()
        }

    }

    fun configureConsumer(topic: String): Consumer<Int, String>? {
        val consumerProps = KafkaTestUtils.consumerProps("testGroup", "true", embeddedKafkaBroker)
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        val consumer = DefaultKafkaConsumerFactory<Int, String>(consumerProps).createConsumer()
        consumer.subscribe(listOf(topic))
        return consumer
    }

    private fun configureProducer(): Producer<String, String> {
        val props = KafkaTestUtils.producerProps(embeddedKafkaBroker)
        props.replace("key.serializer", StringSerializer::class.java)
        val producerProps: Map<String, Any> = HashMap(props)
        return DefaultKafkaProducerFactory<String, String>(producerProps).createProducer()
    }

    fun sendMeldingTilDokumentHendelse(melding: DokumentHendelse) {
        configureProducer().send(ProducerRecord(topicDokument, jsonToString(melding)))
    }


    private fun jsonToString(data: Any): String {
        return try {
            ObjectMapper().findAndRegisterModules().writeValueAsString(data)
        } catch (e: JsonProcessingException) {
            Assert.fail(e.message)
            ""
        }
    }
}