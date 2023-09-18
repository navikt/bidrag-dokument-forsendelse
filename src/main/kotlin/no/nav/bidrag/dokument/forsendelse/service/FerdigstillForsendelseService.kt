package no.nav.bidrag.dokument.forsendelse.service

import jakarta.transaction.Transactional
import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDtoIdType
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.dto.MottakUtsendingKanal
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.opprettReferanseId
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanFerdigstilleForsendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumentDato
import no.nav.bidrag.dokument.forsendelse.utvidelser.erNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import org.springframework.stereotype.Service
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
class FerdigstillForsendelseService(
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val fysiskDokumentService: FysiskDokumentService
) {
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun ferdigstillOgHentForsendelse(
        forsendelseId: Long,
        lokalUtskrift: Boolean = false
    ): Forsendelse? {
        ferdigstillForsendelse(forsendelseId, lokalUtskrift)
        return forsendelseTjeneste.medForsendelseId(forsendelseId)
    }

    @Transactional
    fun ferdigstillForsendelse(
        forsendelseId: Long,
        lokalUtskrift: Boolean = false
    ): OpprettJournalpostResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanFerdigstilleForsendelse()
        log.info { "Ferdigstiller forsendelse $forsendelseId med type ${forsendelse.forsendelseType} og tema ${forsendelse.tema}." }

        val hovedtittel = forsendelse.dokumenter.hoveddokument?.tittel!!
        // Hvis forsendelse blir sendt lokalt så vil saksbehandler skrive ut forsendelse og evt vedlegg manuelt og sende alt sammen via posten
        // Forsendelsen vil fortsatt være synlig på Nav.no etter lokal utskrift er valgt. Det legges derfor på beskjed til bruker at resten av forsendelsen (vedleggene) kommer i posten
        val hovedtittelMedBeskjed = if (lokalUtskrift) opprettTittelMedBeskjedForLokalUtskrift(hovedtittel) else hovedtittel

        val referanseId = forsendelse.opprettReferanseId()
        val opprettJournalpostRequest = OpprettJournalpostRequest(
            avsenderMottaker = if (!forsendelse.erNotat) {
                AvsenderMottakerDto(
                    ident = forsendelse.mottaker!!.ident,
                    navn = forsendelse.mottaker.navn,
                    type = when (forsendelse.mottaker.identType) {
                        MottakerIdentType.SAMHANDLER -> AvsenderMottakerDtoIdType.SAMHANDLER
                        else -> AvsenderMottakerDtoIdType.FNR
                    }
                )
            } else {
                null
            },
            referanseId = referanseId,
            gjelderIdent = forsendelse.gjelderIdent,
            journalførendeEnhet = forsendelse.enhet,
            journalposttype = when (forsendelse.forsendelseType) {
                ForsendelseType.UTGÅENDE -> JournalpostType.UTGÅENDE
                ForsendelseType.NOTAT -> JournalpostType.NOTAT
            },
            kanal = if (lokalUtskrift) MottakUtsendingKanal.LOKAL_UTSKRIFT else null,
            dokumenter = forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                OpprettDokumentDto(
                    brevkode = it.dokumentmalId,
                    tittel = if (it.tilknyttetSom === DokumentTilknyttetSom.HOVEDDOKUMENT) hovedtittelMedBeskjed else it.tittel,
                    dokumentreferanse = it.dokumentreferanse
                )
            },
            tilknyttSaker = listOf(forsendelse.saksnummer),
            saksbehandlerIdent = if (saksbehandlerInfoManager.erApplikasjonBruker()) forsendelse.opprettetAvIdent else null,
            skalFerdigstilles = true,
            tema = when (forsendelse.tema) {
                ForsendelseTema.FAR -> "FAR"
                else -> "BID"
            },
            tittel = hovedtittel,
            datoDokument = if (forsendelse.erNotat) forsendelse.dokumentDato else null
        )

        val respons = bidragDokumentConsumer.opprettJournalpost(opprettJournalpostRequest)

        forsendelseTjeneste.lagre(
            forsendelse.copy(
                journalpostIdFagarkiv = respons!!.journalpostId,
                status = ForsendelseStatus.FERDIGSTILT,
                dokumenter = forsendelse.dokumenter.mapIndexed { i, it ->
                    it.copy(
                        dokumentreferanseFagarkiv = if (respons.dokumenter.size > i) respons.dokumenter[i].dokumentreferanse else null
                    )
                },
                ferdigstiltTidspunkt = LocalDateTime.now(),
                referanseId = referanseId
            )
        )

        log.info { "Ferdigstilt og opprettet journalpost for forsendelse $forsendelseId med type ${forsendelse.forsendelseType}. Opprettet journalpostId=${respons.journalpostId}." }

        return respons
    }

    fun opprettTittelMedBeskjedForLokalUtskrift(tittel: String): String {
        val beskjed = "dokumentet er sendt per post med vedlegg"
        return "$tittel ($beskjed)"
    }
}
