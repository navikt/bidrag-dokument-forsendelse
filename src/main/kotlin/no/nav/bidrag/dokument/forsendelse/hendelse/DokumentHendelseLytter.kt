package no.nav.bidrag.dokument.forsendelse.hendelse

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DokumentHendelse
import no.nav.bidrag.dokument.dto.DokumentHendelseType
import no.nav.bidrag.dokument.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.tjeneste.DokumentTjeneste
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class DokumentHendelseLytter(val objectMapper: ObjectMapper, val dokumentTjeneste: DokumentTjeneste) {

    @KafkaListener(groupId = "bidrag-dokument-forsendelse", topics = ["\${TOPIC_DOKUMENT}"])
    fun prossesserDokumentHendelse(melding: ConsumerRecord<String, String>){
        val hendelse = tilDokumentHendelseObjekt(melding)

        if (hendelse.hendelseType == DokumentHendelseType.BESTILLING) return

        val dokumenter = dokumentTjeneste.hentDokumenterMedReferanse(hendelse.dokumentreferanse)

        dokumenter.forEach {
            dokumentTjeneste.lagreDokument(
                it.copy(
                    arkivsystem = DokumentArkivSystem.MIDL_BREVLAGER,
                    dokumentStatus = when(hendelse.status){
                        DokumentStatusTo.UNDER_REDIGERING -> DokumentStatus.UNDER_REDIGERING
                        DokumentStatusTo.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
                        DokumentStatusTo.AVBRUTT -> DokumentStatus.AVBRUTT
                        else -> it.dokumentStatus
                    }

                )
            )
        }

    }


    private fun tilDokumentHendelseObjekt(melding: ConsumerRecord<String, String>): DokumentHendelse{
        try {
            return objectMapper.readValue(melding.value(), DokumentHendelse::class.java)
        } catch (e: Exception){
            log.error("Det skjedde en feil ved konverting av melding fra hendelse", e)
            throw e
        }
    }
}