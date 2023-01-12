package no.nav.bidrag.dokument.forsendelse.tjeneste

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragDokumentKonsumer
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilAdresseDo
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilIdentType
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilMottakerDo
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.validering.ForespørselValidering.validerKanEndreForsendelse
import no.nav.bidrag.dokument.forsendelse.tjeneste.validering.ForespørselValidering.validerKanFerdigstilleForsendelse
import no.nav.bidrag.dokument.forsendelse.tjeneste.validering.ForespørselValidering.validerKanLeggeTilDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.erNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.journalpostIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.validerGyldigEndring
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import javax.transaction.Transactional

private val log = KotlinLogging.logger {}

@Component
@Transactional
class OppdaterForsendelseTjeneste(
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val dokumentTjeneste: DokumentTjeneste,
    private val bidragDokumentKonsumer: BidragDokumentKonsumer,
    private val fysiskDokumentTjeneste: FysiskDokumentTjeneste
) {

    fun oppdaterForsendelse(forsendelseId: Long, forespørsel: OppdaterForsendelseForespørsel): OppdaterForsendelseResponse {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        forespørsel.validerGyldigEndring(forsendelse)

        log.info { "Oppdaterer forsendelse $forsendelseId" }

        val oppdatertForsendelse = forsendelseTjeneste.lagre(forsendelse.copy(
            dokumenter = oppdaterDokumenter(forsendelse, forespørsel)
        ))


        return OppdaterForsendelseResponse(
            forsendelseId = oppdatertForsendelse.forsendelseId.toString(),
            dokumenter = oppdatertForsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel
                )
            }
        )
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun ferdigstillOgHentForsendelse(forsendelseId: Long): Forsendelse? {
        ferdigstillForsendelse(forsendelseId)
        return forsendelseTjeneste.medForsendelseId(forsendelseId)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun ferdigstillForsendelse(forsendelseId: Long): OpprettJournalpostResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanFerdigstilleForsendelse()
        log.info { "Ferdigstiller forsendelse $forsendelseId med type ${forsendelse.forsendelseType}." }

        val opprettJournalpostRequest = OpprettJournalpostRequest(
            avsenderMottaker = if (!forsendelse.erNotat) AvsenderMottakerDto(
                ident = forsendelse.mottaker!!.ident,
                navn = forsendelse.mottaker.navn
            ) else null,
            referanseId = "BIF_${forsendelse.forsendelseId}",
            gjelderIdent = forsendelse.gjelderIdent,
            journalførendeEnhet = forsendelse.enhet,
            journalposttype = when (forsendelse.forsendelseType) {
                ForsendelseType.UTGÅENDE -> JournalpostType.UTGÅENDE
                ForsendelseType.NOTAT -> JournalpostType.NOTAT
            },
            dokumenter = forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                OpprettDokumentDto(
                    brevkode = it.dokumentmalId,
                    tittel = it.tittel,
                    fysiskDokument = hentFysiskDokument(it)
                )
            },
            tilknyttSaker = listOf(forsendelse.saksnummer),
            saksbehandlerIdent = if (saksbehandlerInfoManager.erApplikasjonBruker()) forsendelse.opprettetAvIdent else null,
            skalFerdigstilles = true
        )

        val respons = bidragDokumentKonsumer.opprettJournalpost(opprettJournalpostRequest)

        forsendelseTjeneste.lagre(forsendelse.copy(
            fagarkivJournalpostId = respons!!.journalpostId,
            status = ForsendelseStatus.FERDIGSTILT,
            dokumenter = forsendelse.dokumenter.mapIndexed { i, it ->
                it.copy(
                    fagrkivDokumentreferanse = if (respons.dokumenter.size > i) respons.dokumenter[i].dokumentreferanse else null
                )
            },
            ferdigstiltTidspunkt = LocalDateTime.now())
        )

        log.info { "Ferdigstilt og opprettet journalpost for forsendelse $forsendelseId med type ${forsendelse.forsendelseType}. Opprettet journalpostId=${respons.journalpostId}." }

        return respons

    }

    fun hentFysiskDokument(dokument: Dokument): ByteArray {
       return if (dokument.arkivsystem == DokumentArkivSystem.BIDRAG) fysiskDokumentTjeneste.hentDokument(dokument.forsendelse.forsendelseId!!, dokument.dokumentreferanse)
       else bidragDokumentKonsumer.hentDokument(dokument.journalpostIdMedPrefix, dokument.dokumentreferanse)!!
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

        if (oppdaterteDokumenter.isEmpty()) throw UgyldigForespørsel("Kan ikke slette alle dokumenter fra forsendelse")

        forsendelseTjeneste.lagre(forsendelse
            .copy(dokumenter = oppdaterteDokumenter.sortertEtterRekkefølge,)
        )

        return OppdaterForsendelseResponse(
            forsendelseId = forsendelse.forsendelseId.toString(),
            dokumenter = forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel
                )
            }
        )
    }
    fun knyttDokumentTilForsendelse(forsendelseId: Long, forespørsel: OpprettDokumentForespørsel): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        forespørsel.validerKanLeggeTilDokument(forsendelse)

        val nyDokument = dokumentTjeneste.opprettNyttDokument(forsendelse, forespørsel)

        log.info { "Knyttet nytt dokument til $forsendelseId med tittel=${forespørsel.tittel}, dokumentmalId=${forespørsel.dokumentmalId}, dokumentreferanse=${nyDokument.dokumentreferanse} og journalpostId=${nyDokument.journalpostId}" }

        return DokumentRespons(
            dokumentreferanse = nyDokument.dokumentreferanse,
            tittel = nyDokument.tittel,
            journalpostId = nyDokument.journalpostId
        )
    }

    private fun oppdaterDokumenter(forsendelse: Forsendelse, forespørsel: OppdaterForsendelseForespørsel): List<Dokument> {

        val oppdaterteDokumenter = forsendelse.dokumenter
            .mapIndexed { i, it ->
                val oppdaterDokument = forespørsel.hentDokument(it.dokumentreferanse)
                val indeks = forespørsel.dokumenter.indexOf(oppdaterDokument)
                it.copy(
                    tittel = oppdaterDokument?.tittel ?: it.tittel,
                    rekkefølgeIndeks = indeks
                )
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    private fun oppdaterDokumenterOld(forsendelse: Forsendelse, forespørsel: OppdaterForsendelseForespørsel): List<Dokument> {
        val oppdaterteDokumenterFraForespørsel = forespørsel.dokumenter
//        val eksisterendeDokumenter = forsendelse.dokumenter
//        val nyeDokumenterFraForespørsel = oppdaterteDokumenterFraForespørsel.filter{!it.fjernTilknytning }.filter { dokumentFraForespørsel -> !eksisterendeDokumenter.any {  dokumentFraForespørsel.dokumentreferanse == it.dokumentreferanse} }
//        val nyeDokumenter = dokumentTjeneste.opprettNyDokument(forsendelse, nyeDokumenterFraForespørsel)

//        val oppdatertHoveddokumentReferanse = oppdaterteDokumenterFraForespørsel.find { it.tilknyttetSom == DokumentTilknyttetSomTo.HOVEDDOKUMENT }?.dokumentreferanse

        val oppdaterteDokumenter = forsendelse.dokumenter//.filter{!forespørsel.skalDokumentSlettes(it.dokumentreferanse) || it.eksternDokumentreferanse == null}
            .map {
                val oppdaterDokument = forespørsel.hentDokument(it.dokumentreferanse)
                it.copy(
                    tittel = oppdaterDokument?.tittel ?: it.tittel,
                    dokumentmalId = oppdaterDokument?.dokumentmalId ?: it.dokumentmalId,
//                    tilknyttetSom = when (oppdatertHoveddokumentReferanse) {
//                        null -> it.tilknyttetSom
//                        it.dokumentreferanse -> DokumentTilknyttetSom.HOVEDDOKUMENT
//                        else -> DokumentTilknyttetSom.VEDLEGG
//                    },
                    //slettetTidspunkt = if (forespørsel.skalDokumentSlettes(it.dokumentreferanse)) LocalDate.now() else null
                )
            } //+ nyeDokumenter

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }
    private fun oppdaterMottaker(eksisterendeMottaker: Mottaker?, oppdatertMottaker: MottakerTo?): Mottaker?{
        if (oppdatertMottaker == null) return eksisterendeMottaker
        if (eksisterendeMottaker == null) return oppdatertMottaker.tilMottakerDo()

        return eksisterendeMottaker.copy(
            navn = oppdatertMottaker.navn ?: eksisterendeMottaker.navn,
            ident = oppdatertMottaker.ident ?: eksisterendeMottaker.ident,
            identType = oppdatertMottaker.tilIdentType(eksisterendeMottaker.identType),
            adresse = oppdaterAdresse(eksisterendeMottaker.adresse, oppdatertMottaker.adresse)
        )
    }

    private fun oppdaterAdresse(eksisterendeAdresse: Adresse? = null, oppdatertAdresse: MottakerAdresseTo?): Adresse?{
        if (oppdatertAdresse == null) return eksisterendeAdresse
        if (eksisterendeAdresse == null) return oppdatertAdresse.tilAdresseDo()
        return oppdatertAdresse.tilAdresseDo().copy(id = eksisterendeAdresse.id)
    }
}