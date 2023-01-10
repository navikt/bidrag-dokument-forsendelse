package no.nav.bidrag.dokument.forsendelse.tjeneste.dao

import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.utenPrefiks
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.repository.DokumentRepository
import no.nav.bidrag.dokument.forsendelse.model.Dokumentreferanse
import no.nav.bidrag.dokument.forsendelse.tjeneste.DokumentBestillingTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.alleMedMinstEtHoveddokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilDokumentDo
import org.springframework.stereotype.Component
import javax.transaction.Transactional

@Component
class DokumentTjeneste(private val dokumentRepository: DokumentRepository, private val dokumentBestillingTjeneste: DokumentBestillingTjeneste) {
    fun opprettNyDokument(forsendelse: Forsendelse, forespørsel: OpprettDokumentForespørsel): Dokument {
        val nyDokument = forespørsel.tilDokumentDo(forsendelse)

        return lagreDokument(nyDokument)
    }

    fun opprettNyDokument(forsendelse: Forsendelse, forespørsel: List<OpprettDokumentForespørsel>): List<Dokument> {
        val nyeDokumenter = forespørsel.map { it.tilDokumentDo(forsendelse) }
        return lagreDokumenter(nyeDokumenter.alleMedMinstEtHoveddokument)
    }

    @Transactional
    fun lagreDokument(dokument: Dokument): Dokument{
        val nyDokument = dokumentRepository.save(dokument)
        bestillDokumentHvisNødvendig(nyDokument)
        return nyDokument
    }

    @Transactional
    fun lagreDokumenter(dokument: List<Dokument>): List<Dokument>{
        val nyDokumenter = dokumentRepository.saveAll(dokument).toList()

        nyDokumenter.forEach { bestillDokumentHvisNødvendig(it) }
        return nyDokumenter
    }

    fun hentDokumenterMedReferanse(dokumentreferanse: Dokumentreferanse): List<Dokument> {
        return dokumentRepository.hentDokumenterMedDokumentreferanse(dokumentreferanse, dokumentreferanse.utenPrefiks.toLong())
    }

    private fun bestillDokumentHvisNødvendig(dokument: Dokument){
        if (dokument.dokumentStatus == DokumentStatus.IKKE_BESTILT){
            dokumentBestillingTjeneste.bestill(dokument.forsendelse.forsendelseId!!, dokument.dokumentreferanse)
        }
    }
}