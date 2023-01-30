package no.nav.bidrag.dokument.forsendelse.hendelse

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import no.nav.bidrag.commons.security.SikkerhetsKontekst.medApplikasjonKontekst
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.DokumentHendelse
import no.nav.bidrag.dokument.dto.DokumentHendelseType
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.service.OppdaterForsendelseService
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class DokumentHendelseLytter(val objectMapper: ObjectMapper, val dokumentTjeneste: DokumentTjeneste, val oppdaterForsendelseService: OppdaterForsendelseService) {

    @KafkaListener(groupId = "bidrag-dokument-forsendelse", topics = ["\${TOPIC_DOKUMENT}"])
    fun prossesserDokumentHendelse(melding: ConsumerRecord<String, String>) {
        val hendelse = tilDokumentHendelseObjekt(melding)

        if (hendelse.hendelseType == DokumentHendelseType.BESTILLING) return

        log.info { "Mottok hendelse for dokumentreferanse ${hendelse.dokumentreferanse} med status ${hendelse.status}, arkivsystem ${hendelse.arkivSystem} og hendelsetype ${hendelse.hendelseType}" }

        val dokumenter = dokumentTjeneste.hentDokumenterMedReferanse(hendelse.dokumentreferanse)

        val oppdaterteDokumenter = dokumenter.map {
            log.info { "Oppdaterer dokument ${it.dokumentId} med dokumentreferanse ${it.dokumentreferanse} og journalpostid ${it.journalpostId} fra forsendelse ${it.forsendelse.forsendelseId} med informasjon fra hendelse" }
            dokumentTjeneste.lagreDokument(
                    it.copy(
                            arkivsystem = when (hendelse.arkivSystem) {
                                DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                                else -> it.arkivsystem
                            },
                            dokumentStatus = when (hendelse.status) {
                                DokumentStatusDto.UNDER_REDIGERING -> DokumentStatus.UNDER_REDIGERING
                                DokumentStatusDto.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
                                DokumentStatusDto.AVBRUTT -> DokumentStatus.AVBRUTT
                                else -> it.dokumentStatus
                            }

                    )
            )
        }

        ferdigstillHvisForsendelseErNotat(oppdaterteDokumenter)

    }

    private fun ferdigstillHvisForsendelseErNotat(dokumenter: List<Dokument>) {
        dokumenter.forEach {
            val forsendelse = it.forsendelse

            if (forsendelse.forsendelseType == ForsendelseType.NOTAT && forsendelse.dokumenter.erAlleFerdigstilt) {
                medApplikasjonKontekst {
                    log.info { "Alle dokumenter i forsendelse ${forsendelse.forsendelseId} med type NOTAT er ferdigstilt. Ferdigstiller forsendelse." }
                    oppdaterForsendelseService.ferdigstillForsendelse(forsendelse.forsendelseId!!)
                }
            }
        }

    }


    private fun tilDokumentHendelseObjekt(melding: ConsumerRecord<String, String>): DokumentHendelse {
        try {
            return objectMapper.readValue(melding.value(), DokumentHendelse::class.java)
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved konverting av melding fra hendelse", e)
            throw e
        }
    }
}