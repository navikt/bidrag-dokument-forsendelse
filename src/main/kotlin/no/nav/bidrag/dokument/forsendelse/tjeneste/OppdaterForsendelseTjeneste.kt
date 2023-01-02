package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentTilknyttetSomTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragDokumentKonsumer
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hent
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.alleMedMinstEnHoveddokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hoveddokumentFørst
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.journalpostIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.skalDokumentSlettes
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilAdresse
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilIdentType
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilMottaker
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.validerKanEndreForsendelse
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.validerKanFerdigstilleForsendelse
import org.springframework.stereotype.Component
import java.time.LocalDate
import javax.transaction.Transactional



@Component
@Transactional
class OppdaterForsendelseTjeneste(val forsendelseTjeneste: ForsendelseTjeneste, val dokumentTjeneste: DokumentTjeneste, val bidragDokumentKonsumer: BidragDokumentKonsumer, val hentDokumentTjeneste: HentDokumentTjeneste) {

    fun oppdaterForsendelse(forsendelseId: Long, forespørsel: OppdaterForsendelseForespørsel): OppdaterForsendelseResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanEndreForsendelse()
        val oppdatertForsendelse = forsendelseTjeneste.lagre(forsendelse.copy(
            gjelderIdent = forespørsel.gjelderIdent ?: forsendelse.gjelderIdent,
            mottaker = oppdaterMottaker(forsendelse.mottaker, forespørsel.mottaker),
            saksnummer = forespørsel.saksnummer ?: forsendelse.saksnummer,
            enhet = forespørsel.enhet ?: forsendelse.enhet,
            språk = forespørsel.språk ?: forsendelse.språk,
            dokumenter = oppdaterDokumenter(forsendelse, forespørsel)
        ))


        return OppdaterForsendelseResponse(
            forsendelseId = oppdatertForsendelse.forsendelseId.toString(),
            dokumenter = oppdatertForsendelse.dokumenter.hoveddokumentFørst.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel
                )
            }
        )
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun ferdigstillForsendelse(forsendelseId: Long): OpprettJournalpostResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanFerdigstilleForsendelse()

        val opprettJournalpostRequest = OpprettJournalpostRequest(
            avsenderMottaker = AvsenderMottakerDto(
                ident = forsendelse.mottaker!!.ident,
                navn = forsendelse.mottaker.navn
            ),
            referanseId = "BIF_${forsendelse.forsendelseId}",
            gjelderIdent = forsendelse.gjelderIdent,
            journalførendeEnhet = forsendelse.enhet,
            journalposttype = when (forsendelse.forsendelseType) {
                ForsendelseType.UTGÅENDE -> JournalpostType.UTGÅENDE
                ForsendelseType.NOTAT -> JournalpostType.NOTAT
            },
            dokumenter = forsendelse.dokumenter.hoveddokumentFørst.map {
                OpprettDokumentDto(
                    brevkode = it.dokumentmalId,
                    dokumentreferanse = it.eksternDokumentreferanse,
                    tittel = it.tittel,
                    fysiskDokument = hentFysiskDokument(it)

                )
            },
            tilknyttSaker = listOf(forsendelse.saksnummer),
            skalFerdigstilles = true
        )

        val respons = bidragDokumentKonsumer.opprettJournalpost(opprettJournalpostRequest)

        forsendelseTjeneste.lagre(forsendelse.copy(arkivJournalpostId = respons!!.journalpostId, status = ForsendelseStatus.FERDIGSTILT))

        return respons

    }

    fun hentFysiskDokument(dokument: Dokument): ByteArray {
       return if (dokument.arkivsystem == DokumentArkivSystem.BIDRAG) hentDokumentTjeneste.hentDokument(dokument.forsendelse.forsendelseId!!, dokument.dokumentreferanse)
       else bidragDokumentKonsumer.hentDokument(dokument.journalpostIdMedPrefix!!, dokument.eksternDokumentreferanse)!!
    }
    fun fjernDokumentFraForsendelse(forsendelseId: Long, dokumentreferanse: String): OppdaterForsendelseResponse?{
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanEndreForsendelse()


        val oppdaterteDokumenter = forsendelse.dokumenter
            .filter { it.dokumentreferanse != dokumentreferanse || it.eksternDokumentreferanse == null }.map {
                it.copy(
                    slettetTidspunkt = if (it.dokumentreferanse == dokumentreferanse) LocalDate.now() else null,
                    tilknyttetSom = DokumentTilknyttetSom.VEDLEGG
                )
            }

        if (oppdaterteDokumenter.isEmpty()){
            throw UgyldigForespørsel("Kan ikke slette alle dokumenter fra forsendelse")
        }

        forsendelseTjeneste.lagre(forsendelse.copy(dokumenter = oppdaterteDokumenter.alleMedMinstEnHoveddokument))

        return OppdaterForsendelseResponse(
            forsendelseId = forsendelse.forsendelseId.toString(),
            dokumenter = forsendelse.dokumenter.hoveddokumentFørst.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel
                )
            }
        )
    }
    fun knyttDokumentTilForsendelse(forsendelseId: Long, forespørsel: OpprettDokumentForespørsel): DokumentRespons? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanEndreForsendelse()

        if (forsendelse.dokumenter.hent(forespørsel.dokumentreferanse) != null){
            throw UgyldigForespørsel("Forsendelse $forsendelseId har allerede tilknyttet dokument med dokumentreferanse ${forespørsel.dokumentreferanse}")
        }

        if (forespørsel.tilknyttetSom == DokumentTilknyttetSomTo.HOVEDDOKUMENT){
            forsendelseTjeneste.lagre(forsendelse.copy(dokumenter = forsendelse.dokumenter.map { it.copy(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG) }))
        }

        val nyDokument = dokumentTjeneste.opprettNyDokument(forsendelse, forespørsel)

        return DokumentRespons(
            dokumentreferanse = nyDokument.dokumentreferanse,
            tittel = nyDokument.tittel,
            journalpostId = nyDokument.journalpostId
        )
    }

    private fun oppdaterDokumenter(forsendelse: Forsendelse, forespørsel: OppdaterForsendelseForespørsel): List<Dokument> {
        val oppdaterteDokumenterFraForespørsel = forespørsel.dokumenter
        val eksisterendeDokumenter = forsendelse.dokumenter
        val nyeDokumenterFraForespørsel = oppdaterteDokumenterFraForespørsel.filter{!it.fjernTilknytning }.filter { dokumentFraForespørsel -> !eksisterendeDokumenter.any {  dokumentFraForespørsel.dokumentreferanse == it.dokumentreferanse} }
        val nyeDokumenter = dokumentTjeneste.opprettNyDokument(forsendelse, nyeDokumenterFraForespørsel)

        val oppdatertHoveddokumentReferanse = oppdaterteDokumenterFraForespørsel.find { it.tilknyttetSom == DokumentTilknyttetSomTo.HOVEDDOKUMENT }?.dokumentreferanse

        val oppdaterteDokumenter = forsendelse.dokumenter.filter{!forespørsel.skalDokumentSlettes(it.dokumentreferanse) || it.eksternDokumentreferanse == null}
            .map {
                val oppdaterDokument = forespørsel.hent(it.dokumentreferanse)
                it.copy(
                    tittel = oppdaterDokument?.tittel ?: it.tittel,
                    dokumentmalId = oppdaterDokument?.dokumentmalId ?: it.dokumentmalId,
                    tilknyttetSom = when (oppdatertHoveddokumentReferanse) {
                        null -> it.tilknyttetSom
                        it.dokumentreferanse -> DokumentTilknyttetSom.HOVEDDOKUMENT
                        else -> DokumentTilknyttetSom.VEDLEGG
                    },
                    slettetTidspunkt = if (forespørsel.skalDokumentSlettes(it.dokumentreferanse)) LocalDate.now() else null
                )
            } + nyeDokumenter

        return oppdaterteDokumenter.alleMedMinstEnHoveddokument
    }
    private fun oppdaterMottaker(eksisterendeMottaker: Mottaker?, oppdatertMottaker: MottakerTo?): Mottaker?{
        if (oppdatertMottaker == null) return eksisterendeMottaker
        if (eksisterendeMottaker == null) return oppdatertMottaker.tilMottaker()

        return eksisterendeMottaker.copy(
            navn = oppdatertMottaker.navn ?: eksisterendeMottaker.navn,
            ident = oppdatertMottaker.ident ?: eksisterendeMottaker.ident,
            identType = oppdatertMottaker.tilIdentType(eksisterendeMottaker.identType),
            adresse = oppdaterAdresse(eksisterendeMottaker.adresse, oppdatertMottaker.adresse)
        )
    }

    private fun oppdaterAdresse(eksisterendeAdresse: Adresse? = null, oppdatertAdresse: MottakerAdresseTo?): Adresse?{
        if (oppdatertAdresse == null) return eksisterendeAdresse
        if (eksisterendeAdresse == null) return oppdatertAdresse.tilAdresse()
        return oppdatertAdresse.tilAdresse().copy(id = eksisterendeAdresse.id)
    }
}