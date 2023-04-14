package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDtoIdType
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.dto.MottakUtsendingKanal
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilOpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.kunneIkkeFerdigstilleForsendelse
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.valider
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanEndreForsendelse
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanFerdigstilleForsendelse
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanLeggeTilDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumentDato
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumenterIkkeSlettet
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumenterLogiskSlettet
import no.nav.bidrag.dokument.forsendelse.utvidelser.erNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.skalDokumentSlettes
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.validerGyldigEndring
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import java.time.LocalDate
import java.time.LocalDateTime
import javax.transaction.Transactional

private val log = KotlinLogging.logger {}

@Component
@Transactional
class OppdaterForsendelseService(
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val dokumentTjeneste: DokumentTjeneste,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val fysiskDokumentService: FysiskDokumentService
) {

    fun oppdaterForsendelse(
        forsendelseId: Long,
        forespørsel: OppdaterForsendelseForespørsel
    ): OppdaterForsendelseResponse {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        forespørsel.validerGyldigEndring(forsendelse)

        log.info { "Oppdaterer forsendelse $forsendelseId" }

        val oppdatertForsendelse = forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = oppdaterOgOpprettDokumenter(forsendelse, forespørsel)
            )
        )

        return OppdaterForsendelseResponse(
            forsendelseId = oppdatertForsendelse.forsendelseId.toString(),
            dokumenter = oppdatertForsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel,
                    dokumentDato = it.dokumentDato
                )
            }
        )
    }

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
            referanseId = if (forsendelse.forsendelseId == 1000027583L) "BIF_${forsendelse.forsendelseId}_2" else "BIF_${forsendelse.forsendelseId}",
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
                    tittel = it.tittel,
                    fysiskDokument = hentFysiskDokument(it)
                )
            },
            tilknyttSaker = listOf(forsendelse.saksnummer),
            saksbehandlerIdent = if (saksbehandlerInfoManager.erApplikasjonBruker()) forsendelse.opprettetAvIdent else null,
            skalFerdigstilles = true,
            tema = when (forsendelse.tema) {
                ForsendelseTema.FAR -> "FAR"
                else -> "BID"
            },
            datoDokument = if (forsendelse.erNotat) forsendelse.dokumentDato else null
        )

        val respons = opprettJournalpost(opprettJournalpostRequest, forsendelseId)

        forsendelseTjeneste.lagre(
            forsendelse.copy(
                journalpostIdFagarkiv = respons!!.journalpostId,
                status = ForsendelseStatus.FERDIGSTILT,
                dokumenter = forsendelse.dokumenter.mapIndexed { i, it ->
                    it.copy(
                        dokumentreferanseFagarkiv = if (respons.dokumenter.size > i) respons.dokumenter[i].dokumentreferanse else null
                    )
                },
                ferdigstiltTidspunkt = LocalDateTime.now()
            )
        )

        log.info { "Ferdigstilt og opprettet journalpost for forsendelse $forsendelseId med type ${forsendelse.forsendelseType}. Opprettet journalpostId=${respons.journalpostId}." }

        return respons
    }

    private fun opprettJournalpost(opprettJournalpostRequest: OpprettJournalpostRequest, forsendelseId: Long): OpprettJournalpostResponse? {
        try {
            return bidragDokumentConsumer.opprettJournalpost(opprettJournalpostRequest)
        } catch (ex: HttpStatusCodeException) {
            if (ex.statusCode == HttpStatus.BAD_REQUEST) {
                kunneIkkeFerdigstilleForsendelse(forsendelseId)
            }
            throw ex
        }
    }

    fun hentFysiskDokument(dokument: Dokument): ByteArray {
        return if (dokument.arkivsystem == DokumentArkivSystem.BIDRAG) {
            fysiskDokumentService.hentDokument(
                dokument.forsendelse.forsendelseId!!,
                dokument.dokumentreferanse
            )
        } else {
            bidragDokumentConsumer.hentDokument(
                dokument.journalpostId
                    ?: dokument.forsendelseIdMedPrefix,
                dokument.dokumentreferanse
            )!!
        }
    }

    fun fjernDokumentFraForsendelse(
        forsendelseId: Long,
        dokumentreferanse: String
    ): OppdaterForsendelseResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanEndreForsendelse()

        val oppdaterteDokumenter = forsendelse.dokumenter
            .filter { it.dokumentreferanse != dokumentreferanse || it.dokumentreferanseOriginal == null }
            .map {
                it.copy(
                    slettetTidspunkt = if (it.dokumentreferanse == dokumentreferanse) LocalDate.now() else null
                )
            }

        if (oppdaterteDokumenter.isEmpty()) throw UgyldigForespørsel("Kan ikke slette alle dokumenter fra forsendelse")

        forsendelseTjeneste.lagre(
            forsendelse
                .copy(dokumenter = oppdaterteDokumenter.sortertEtterRekkefølge)
        )

        return OppdaterForsendelseResponse(
            forsendelseId = forsendelse.forsendelseId.toString(),
            dokumenter = forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel,
                    dokumentDato = it.dokumentDato
                )
            }
        )
    }

    fun knyttDokumentTilForsendelse(
        forsendelseId: Long,
        forespørsel: OpprettDokumentForespørsel
    ): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        forespørsel.validerKanLeggeTilDokument(forsendelse)

        val nyDokument = knyttDokumentTilForsendelse(forsendelse, forespørsel)

        return DokumentRespons(
            dokumentreferanse = nyDokument.dokumentreferanse,
            tittel = nyDokument.tittel,
            journalpostId = nyDokument.journalpostId,
            dokumentDato = nyDokument.dokumentDato
        )
    }

    fun knyttDokumentTilForsendelse(
        forsendelse: Forsendelse,
        forespørsel: OpprettDokumentForespørsel
    ): Dokument {
        forsendelse.validerKanEndreForsendelse()
        forespørsel.validerKanLeggeTilDokument(forsendelse)

        val nyDokument = dokumentTjeneste.opprettNyttDokument(forsendelse, forespørsel)

        log.info { "Knyttet nytt dokument til ${forsendelse.forsendelseId} med tittel=${forespørsel.tittel}, språk=${forespørsel.språk} dokumentmalId=${forespørsel.dokumentmalId}, dokumentreferanse=${nyDokument.dokumentreferanse} og journalpostId=${nyDokument.journalpostId}" }

        return nyDokument
    }

    private fun oppdaterDokument(
        forsendelse: Forsendelse,
        dokumentreferanse: String,
        forespørsel: OppdaterDokumentForespørsel
    ): List<Dokument> {
        val oppdaterteDokumenter = forsendelse.dokumenter
            .map {
                if (it.dokumentreferanse == dokumentreferanse) {
                    it.copy(
                        tittel = forespørsel.tittel ?: it.tittel,
                        dokumentDato = forespørsel.dokumentDato ?: it.dokumentDato
                    )
                } else {
                    it
                }
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    private fun oppdaterDokumenter(
        forsendelse: Forsendelse,
        forespørsel: OppdaterForsendelseForespørsel
    ): List<Dokument> {
        val oppdaterteDokumenter = forsendelse.dokumenter
            .mapIndexed { i, it ->
                val oppdaterDokument = forespørsel.hentDokument(it.dokumentreferanse)
                val indeks = forespørsel.dokumenter.indexOf(oppdaterDokument)
                it.copy(
                    tittel = oppdaterDokument?.tittel ?: it.tittel,
                    rekkefølgeIndeks = indeks,
                    dokumentDato = if (indeks == 0) forespørsel.dokumentDato ?: it.dokumentDato else it.dokumentDato
                )
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    private fun oppdaterOgOpprettDokumenter(
        forsendelse: Forsendelse,
        forespørsel: OppdaterForsendelseForespørsel
    ): List<Dokument> {
        val logiskSlettetDokumenterFraForespørsel =
            forsendelse.dokumenter.filter { forespørsel.skalDokumentSlettes(it.dokumentreferanse) && !it.erFraAnnenKilde }
                .map {
                    it.copy(
                        slettetTidspunkt = LocalDate.now()
                    )
                }
        val oppdaterteDokumenter = forespørsel.dokumenter
            .filter { it.fjernTilknytning == false }
            .mapIndexed { indeks, it ->
                val eksisterendeDokument = forsendelse.dokumenter.hentDokument(it.dokumentreferanse)
                eksisterendeDokument?.copy(
                    tittel = it.tittel ?: eksisterendeDokument.tittel,
                    rekkefølgeIndeks = indeks,
                    metadata = it.metadata ?: eksisterendeDokument.metadata,
                    dokumentDato = if (indeks == 0 && forsendelse.erNotat) {
                        forespørsel.dokumentDato
                            ?: eksisterendeDokument.dokumentDato
                    } else {
                        eksisterendeDokument.dokumentDato
                    }
                ) ?: knyttDokumentTilForsendelse(forsendelse, it.tilOpprettDokumentForespørsel())
            } + forsendelse.dokumenter.dokumenterLogiskSlettet + logiskSlettetDokumenterFraForespørsel

        if (oppdaterteDokumenter.dokumenterIkkeSlettet.isEmpty()) throw UgyldigForespørsel("Kan ikke slette alle dokumenter fra forsendelse")
        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    fun oppdaterDokument(forsendelseId: Long, dokumentreferanse: String, forespørsel: OppdaterDokumentForespørsel): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        forespørsel.valider(forsendelse, dokumentreferanse)

        log.info { "Oppdaterer dokument $dokumentreferanse i forsendelse $forsendelseId" }

        val oppdatertForsendelse = forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = oppdaterDokument(forsendelse, dokumentreferanse, forespørsel)
            )
        )

        val oppdatertDokument = oppdatertForsendelse.dokumenter.hentDokument(dokumentreferanse)!!
        return DokumentRespons(
            dokumentreferanse = oppdatertDokument.dokumentreferanse,
            tittel = oppdatertDokument.tittel,
            dokumentDato = oppdatertDokument.dokumentDato
        )
    }
//    private fun oppdaterMottaker(eksisterendeMottaker: Mottaker?, oppdatertMottaker: MottakerTo?): Mottaker?{
//        if (oppdatertMottaker == null) return eksisterendeMottaker
//        if (eksisterendeMottaker == null) return oppdatertMottaker.tilMottakerDo()
//
//        return eksisterendeMottaker.copy(
//            navn = oppdatertMottaker.navn ?: eksisterendeMottaker.navn,
//            ident = oppdatertMottaker.ident ?: eksisterendeMottaker.ident,
//            identType = oppdatertMottaker.tilIdentType(eksisterendeMottaker.identType),
//            adresse = oppdaterAdresse(eksisterendeMottaker.adresse, oppdatertMottaker.adresse)
//        )
//    }

//    private fun oppdaterAdresse(eksisterendeAdresse: Adresse? = null, oppdatertAdresse: MottakerAdresseTo?): Adresse?{
//        if (oppdatertAdresse == null) return eksisterendeAdresse
//        if (eksisterendeAdresse == null) return oppdatertAdresse.tilAdresseDo()
//        return oppdatertAdresse.tilAdresseDo().copy(id = eksisterendeAdresse.id)
//    }
}
