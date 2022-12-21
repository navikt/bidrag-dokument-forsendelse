package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.model.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.model.DokumentTilknyttetSomTo
import no.nav.bidrag.dokument.forsendelse.model.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.model.MottakerDto
import no.nav.bidrag.dokument.forsendelse.model.OppdaterDokumentRespons
import no.nav.bidrag.dokument.forsendelse.model.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.model.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.tilAdresse
import no.nav.bidrag.dokument.forsendelse.model.tilArkivsystemDo
import no.nav.bidrag.dokument.forsendelse.model.tilDokumentDo
import no.nav.bidrag.dokument.forsendelse.model.tilDokumentStatusDo
import no.nav.bidrag.dokument.forsendelse.model.tilIdentType
import no.nav.bidrag.dokument.forsendelse.model.tilMottaker
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Adresse
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Mottaker
import no.nav.bidrag.dokument.forsendelse.persistence.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.persistence.model.hoveddokumentFørst
import no.nav.bidrag.dokument.forsendelse.persistence.repository.DokumentRepository
import no.nav.bidrag.dokument.forsendelse.persistence.repository.ForsendelseRepository
import org.springframework.stereotype.Component
import java.time.LocalDate
import javax.transaction.Transactional

fun OppdaterForsendelseForespørsel.skalDokumentSlettes(dokumentreferanse: String?) = dokumenter.any { it.fjernTilknytning && it.dokumentreferanse == dokumentreferanse}
fun OppdaterForsendelseForespørsel.hentDokument(dokumentreferanse: String?) = dokumenter.find { it.dokumentreferanse == dokumentreferanse }
fun Forsendelse.hentDokument(dokumentreferanse: String?) = dokumenter.find { it.dokumentreferanse == dokumentreferanse }
@Component
@Transactional
class OppdaterForsendelseService(val forsendelseRepository: ForsendelseRepository, val dokumentRepository: DokumentRepository) {

    fun oppdaterForsendelse(forsendelseId: Long, forespørsel: OppdaterForsendelseForespørsel): OppdaterForsendelseResponse? {
        val forsendelse = hentForsendelse(forsendelseId) ?: return null

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
            dokumenter = oppdatertForsendelse.hoveddokumentFørst.map {
                OppdaterDokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel
                )
            }
        )
    }
    fun fjernDokumentFraForsendelse(forsendelseId: Long, dokumentreferanse: String): OppdaterForsendelseResponse?{
        val forsendelse = hentForsendelse(forsendelseId) ?: return null

        forsendelseRepository.save(forsendelse.copy(dokumenter = forsendelse.dokumenter
            .filter { it.dokumentreferanse != dokumentreferanse || it.eksternDokumentreferanse == null }.map {
                it.copy(
                    slettetTidspunkt = if (it.dokumentreferanse == dokumentreferanse) LocalDate.now() else null
                )
            }
        ))

        return OppdaterForsendelseResponse(
            forsendelseId = forsendelse.forsendelseId.toString(),
            dokumenter = forsendelse.hoveddokumentFørst.map {
                OppdaterDokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel
                )
            }
        )
    }
    fun knyttDokumentTilForsendelse(forsendelseId: Long, forespørsel: OpprettDokumentForespørsel): DokumentRespons? {
        val forsendelse = hentForsendelse(forsendelseId) ?: return null

        if (forsendelse.hentDokument(forespørsel.dokumentreferanse) != null){
            throw UgyldigForespørsel("Forsendelse $forsendelseId har allerede tilknyttet dokument med dokumentreferanse ${forespørsel.dokumentreferanse}")
        }

        if (forespørsel.tilknyttetSom == DokumentTilknyttetSomTo.HOVEDDOKUMENT){
            forsendelseRepository.save(forsendelse.copy(dokumenter = forsendelse.dokumenter.map { it.copy(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG) }))
        }

        val nyDokument = forespørsel.tilDokumentDo(forsendelse)

        dokumentRepository.save(nyDokument)

        return DokumentRespons(
            dokumentreferanse = nyDokument.dokumentreferanse,
            tittel = nyDokument.tittel,
            journalpostId = nyDokument.journalpostId
        )
    }

    private fun oppdaterDokumenter(forsendelse: Forsendelse, forespørsel: OppdaterForsendelseForespørsel): List<Dokument> {
        val oppdaterteDokumenter = forespørsel.dokumenter
        val eksisterendeDokumenter = forsendelse.dokumenter
        val nyeDokumenterFraForespørsel = oppdaterteDokumenter.filter{!it.fjernTilknytning }.filter { dokumentFraForespørsel -> !eksisterendeDokumenter.any {  dokumentFraForespørsel.dokumentreferanse == it.dokumentreferanse} }
        val nyeDokumenter = nyeDokumenterFraForespørsel.map {it.tilDokumentDo(forsendelse) }

        dokumentRepository.saveAll(nyeDokumenter)

        val oppdatertHoveddokumentReferanse = oppdaterteDokumenter.find { it.tilknyttetSom == DokumentTilknyttetSomTo.HOVEDDOKUMENT }?.dokumentreferanse

        return forsendelse.dokumenter.filter{!forespørsel.skalDokumentSlettes(it.dokumentreferanse) || it.eksternDokumentreferanse == null}
            .map {
                val oppdaterDokument = forespørsel.hentDokument(it.dokumentreferanse)
                it.copy(
                    tittel = oppdaterDokument?.tittel ?: it.tittel,
                    dokumentmalId = oppdaterDokument?.dokumentmalId ?: it.dokumentmalId,
                    tilknyttetSom = if(oppdatertHoveddokumentReferanse == null) it.tilknyttetSom
                                    else if (oppdatertHoveddokumentReferanse == it.eksternDokumentreferanse) DokumentTilknyttetSom.HOVEDDOKUMENT
                                    else DokumentTilknyttetSom.VEDLEGG,
                    slettetTidspunkt = if (forespørsel.skalDokumentSlettes(it.dokumentreferanse)) LocalDate.now() else null
                )
            } + nyeDokumenter
    }
    private fun oppdaterMottaker(eksisterendeMottaker: Mottaker?, oppdatertMottaker: MottakerDto?): Mottaker?{
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

    private fun hentForsendelse(forsendelseId: Long): Forsendelse?{
        val forsendelseOpt = forsendelseRepository.findById(forsendelseId)
        if (forsendelseOpt.isEmpty){
            return null
        }

        return forsendelseOpt.get()
    }
}