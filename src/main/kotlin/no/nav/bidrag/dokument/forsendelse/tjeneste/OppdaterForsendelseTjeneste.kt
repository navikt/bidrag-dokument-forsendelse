package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentTilknyttetSomTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.model.UgyldigEndringAvForsendelse
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hent
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.alleMedMinstEnHoveddokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hoveddokumentFørst
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.skalDokumentSlettes
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilAdresse
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilIdentType
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilMottaker
import org.springframework.stereotype.Component
import java.time.LocalDate
import javax.transaction.Transactional


fun Forsendelse.validerKanEndreForsendelse(){
    if (this.status != ForsendelseStatus.UNDER_PRODUKSJON){
        throw UgyldigEndringAvForsendelse("Forsendelse med forsendelseId=${this.forsendelseId} og status ${this.status} kan ikke endres")
    }
}
@Component
@Transactional
class OppdaterForsendelseService(val forsendelseRepository: ForsendelseRepository, val dokumentTjeneste: DokumentTjeneste) {

    fun oppdaterForsendelse(forsendelseId: Long, forespørsel: OppdaterForsendelseForespørsel): OppdaterForsendelseResponse? {
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanEndreForsendelse()
        val oppdatertForsendelse = forsendelse.copy(
            gjelderIdent = forespørsel.gjelderIdent ?: forsendelse.gjelderIdent,
            mottaker = oppdaterMottaker(forsendelse.mottaker, forespørsel.mottaker),
            saksnummer = forespørsel.saksnummer ?: forsendelse.saksnummer,
            enhet = forespørsel.enhet ?: forsendelse.enhet,
            dokumenter = oppdaterDokumenter(forsendelse, forespørsel)
        )

        forsendelseRepository.save(oppdatertForsendelse)
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

    fun avbrytForsendelse(forsendelseId: Long): Boolean {
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId) ?: return false
        forsendelse.validerKanEndreForsendelse()

        forsendelseRepository.save(forsendelse.copy(status = ForsendelseStatus.AVBRUTT))

        return true
    }
    fun fjernDokumentFraForsendelse(forsendelseId: Long, dokumentreferanse: String): OppdaterForsendelseResponse?{
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId) ?: return null
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

        forsendelseRepository.save(forsendelse.copy(dokumenter = oppdaterteDokumenter.alleMedMinstEnHoveddokument))

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
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanEndreForsendelse()

        if (forsendelse.dokumenter.hent(forespørsel.dokumentreferanse) != null){
            throw UgyldigForespørsel("Forsendelse $forsendelseId har allerede tilknyttet dokument med dokumentreferanse ${forespørsel.dokumentreferanse}")
        }

        if (forespørsel.tilknyttetSom == DokumentTilknyttetSomTo.HOVEDDOKUMENT){
            forsendelseRepository.save(forsendelse.copy(dokumenter = forsendelse.dokumenter.map { it.copy(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG) }))
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