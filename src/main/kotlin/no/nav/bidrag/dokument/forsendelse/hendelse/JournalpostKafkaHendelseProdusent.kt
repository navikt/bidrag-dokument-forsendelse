package no.nav.bidrag.dokument.forsendelse.hendelse

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import no.nav.bidrag.commons.CorrelationId
import no.nav.bidrag.dokument.dto.HendelseType
import no.nav.bidrag.dokument.dto.JournalpostHendelse
import no.nav.bidrag.dokument.dto.JournalpostStatus
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.dto.Sporingsdata
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import no.nav.bidrag.dokument.forsendelse.utvidelser.erUtgående
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.kanDistribueres
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class JournalpostKafkaHendelseProdusent(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${TOPIC_JOURNALPOST}") val topic: String
) {
    @Retryable(value = [Exception::class], maxAttempts = 10, backoff = Backoff(delay = 1000, maxDelay = 12000, multiplier = 2.0))
    fun publiser(journalpostHendelse: JournalpostHendelse) {
        log.info("Publiserer JournalpostHendelse for forsendelse ${journalpostHendelse.journalpostId}")
        SIKKER_LOGG.info("Publiserer JournalpostHendelse $journalpostHendelse")
        kafkaTemplate.send(topic, journalpostHendelse.journalpostId, objectMapper.writeValueAsString(journalpostHendelse))
    }

    @Retryable(value = [Exception::class], maxAttempts = 10, backoff = Backoff(delay = 1000, maxDelay = 12000, multiplier = 2.0))
    fun publiserForsendelse(forsendelse: Forsendelse) {
        publiser(
            JournalpostHendelse(
                journalpostId = forsendelse.forsendelseIdMedPrefix,
                fnr = forsendelse.gjelderIdent,
                tittel = forsendelse.dokumenter.hoveddokument?.tittel,
                tema = forsendelse.tema.name,
                batchId = forsendelse.batchId,
                hendelseType = HendelseType.ENDRING,
                enhet = forsendelse.enhet,
                dokumentDato = forsendelse.opprettetTidspunkt.toLocalDate(),
                journalfortDato = forsendelse.ferdigstiltTidspunkt?.toLocalDate(),
                sakstilknytninger = listOf(forsendelse.saksnummer),
                sporing = Sporingsdata(CorrelationId.fetchCorrelationIdForThread(), forsendelse.opprettetAvIdent, forsendelse.opprettetAvNavn),
                journalposttype = when (forsendelse.forsendelseType) {
                    ForsendelseType.NOTAT -> JournalpostType.NOTAT.name
                    ForsendelseType.UTGÅENDE -> JournalpostType.UTGÅENDE.name
                },
                status = when (forsendelse.status) {
                    ForsendelseStatus.DISTRIBUERT_LOKALT, ForsendelseStatus.DISTRIBUERT -> JournalpostStatus.DISTRIBUERT.name
                    ForsendelseStatus.SLETTET -> JournalpostStatus.UTGÅR.name
                    ForsendelseStatus.AVBRUTT -> JournalpostStatus.FEILREGISTRERT.name
                    ForsendelseStatus.FERDIGSTILT -> if (forsendelse.distribusjonKanal == DistribusjonKanal.INGEN_DISTRIBUSJON) ForsendelseStatus.DISTRIBUERT.name
                    else if (forsendelse.erUtgående) JournalpostStatus.KLAR_FOR_DISTRIBUSJON.name
                    else JournalpostStatus.FERDIGSTILT.name

                    ForsendelseStatus.UNDER_PRODUKSJON ->
                        if (forsendelse.dokumenter.erAlleFerdigstilt)
                            if (forsendelse.kanDistribueres()) JournalpostStatus.KLAR_FOR_DISTRIBUSJON.name else JournalpostStatus.FERDIGSTILT.name
                        else JournalpostStatus.UNDER_PRODUKSJON.name

                }
            )
        )
    }
}
