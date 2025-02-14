package no.nav.bidrag.dokument.forsendelse.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import no.nav.bidrag.commons.util.secureLogger
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
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilDto
import no.nav.bidrag.transport.dokument.AvsenderMottakerDto
import no.nav.bidrag.transport.dokument.AvsenderMottakerDtoIdType
import no.nav.bidrag.transport.dokument.JournalpostType
import no.nav.bidrag.transport.dokument.MottakUtsendingKanal
import no.nav.bidrag.transport.dokument.OpprettDokumentDto
import no.nav.bidrag.transport.dokument.OpprettJournalpostRequest
import no.nav.bidrag.transport.dokument.OpprettJournalpostResponse
import org.springframework.stereotype.Service
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
class FerdigstillForsendelseService(
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val fysiskDokumentService: FysiskDokumentService,
) {
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun ferdigstillOgHentForsendelse(
        forsendelseId: Long,
        lokalUtskrift: Boolean = false,
        ingenDistribusjon: Boolean = false,
    ): Forsendelse? {
        ferdigstillForsendelse(forsendelseId, lokalUtskrift, ingenDistribusjon)
        return forsendelseTjeneste.medForsendelseId(forsendelseId)
    }

    @Transactional
    fun ferdigstillForsendelse(
        forsendelseId: Long,
        lokalUtskrift: Boolean = false,
        ingenDistribusjon: Boolean = false,
    ): OpprettJournalpostResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanFerdigstilleForsendelse(lokalUtskrift, ingenDistribusjon)
        log.info { "Ferdigstiller forsendelse $forsendelseId med type ${forsendelse.forsendelseType} og tema ${forsendelse.tema}." }

        val hovedtittel = forsendelse.dokumenter.hoveddokument?.tittel!!
        // Hvis forsendelse blir sendt lokalt så vil saksbehandler skrive ut forsendelse og evt vedlegg manuelt og sende alt sammen via posten
        // Forsendelsen vil fortsatt være synlig på Nav.no etter lokal utskrift er valgt. Det legges derfor på beskjed til bruker at resten av forsendelsen (vedleggene) kommer i posten
        val hovedtittelMedBeskjed = if (lokalUtskrift) opprettTittelMedBeskjedForLokalUtskrift(hovedtittel) else hovedtittel

        val referanseId = forsendelse.opprettReferanseId()
        val dokumentDato = forsendelse.dokumentDato?.let { begrensDokumentdatoTilIdagEllerTidligere(it) }
        val opprettJournalpostRequest =
            OpprettJournalpostRequest(
                avsenderMottaker =
                    if (!forsendelse.erNotat) {
                        AvsenderMottakerDto(
                            ident = forsendelse.mottaker!!.ident,
                            navn = forsendelse.mottaker.navn,
                            type =
                                when (forsendelse.mottaker.identType) {
                                    MottakerIdentType.SAMHANDLER -> AvsenderMottakerDtoIdType.SAMHANDLER
                                    else -> AvsenderMottakerDtoIdType.FNR
                                },
                        )
                    } else {
                        null
                    },
                referanseId = referanseId,
                gjelderIdent = forsendelse.gjelderIdent,
                journalførendeEnhet = forsendelse.enhet,
                journalposttype =
                    when (forsendelse.forsendelseType) {
                        ForsendelseType.UTGÅENDE -> JournalpostType.UTGÅENDE
                        ForsendelseType.NOTAT -> JournalpostType.NOTAT
                    },
                kanal =
                    if (lokalUtskrift) {
                        MottakUtsendingKanal.LOKAL_UTSKRIFT
                    } else if (ingenDistribusjon) {
                        MottakUtsendingKanal.INGEN_DISTRIBUSJON
                    } else {
                        null
                    },
                dokumenter =
                    forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                        OpprettDokumentDto(
                            brevkode = it.dokumentmalId,
                            tittel = if (it.tilknyttetSom === DokumentTilknyttetSom.HOVEDDOKUMENT) hovedtittelMedBeskjed else it.tittel,
                            dokumentreferanse = it.dokumentreferanse,
                        )
                    },
                tilknyttSaker = listOf(forsendelse.saksnummer),
                saksbehandlerIdent = if (saksbehandlerInfoManager.erApplikasjonBruker()) forsendelse.opprettetAvIdent else null,
                skalFerdigstilles = true,
                tema =
                    when (forsendelse.tema) {
                        ForsendelseTema.FAR -> "FAR"
                        else -> "BID"
                    },
                tittel = hovedtittel,
                datoDokument = if (forsendelse.erNotat) dokumentDato else null,
                ettersendingsoppgave = forsendelse.ettersendingsoppgave?.tilDto(),
            )

        secureLogger.info { "Oppretter journalpost for forsendelse $forsendelseId med forespørsel $opprettJournalpostRequest" }

        val respons = bidragDokumentConsumer.opprettJournalpost(opprettJournalpostRequest)

        secureLogger.info { "Opprettet journalpost for forsendelse $forsendelseId med respons $respons" }

        forsendelseTjeneste.lagre(
            forsendelse.copy(
                journalpostIdFagarkiv = respons!!.journalpostId,
                status = ForsendelseStatus.FERDIGSTILT,
                dokumenter =
                    forsendelse.dokumenter.mapIndexed { i, it ->
                        it.copy(
                            dokumentreferanseFagarkiv = if (respons.dokumenter.size > i) respons.dokumenter[i].dokumentreferanse else null,
                            dokumentDato = if (forsendelse.erNotat && dokumentDato != null) dokumentDato else it.dokumentDato,
                        )
                    },
                ferdigstiltTidspunkt = LocalDateTime.now(),
                referanseId = referanseId,
            ),
        )

        log.info {
            "Ferdigstilt og opprettet journalpost for forsendelse $forsendelseId med type ${forsendelse.forsendelseType}. " +
                "Opprettet journalpostId=${respons.journalpostId}."
        }

        return respons
    }

    fun begrensDokumentdatoTilIdagEllerTidligere(date: LocalDateTime): LocalDateTime =
        if (date.isAfter(LocalDateTime.now())) LocalDateTime.now() else date

    fun opprettTittelMedBeskjedForLokalUtskrift(tittel: String): String {
        val beskjed = "dokumentet er sendt per post med vedlegg"
        return "$tittel ($beskjed)"
    }
}
