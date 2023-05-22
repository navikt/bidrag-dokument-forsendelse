package no.nav.bidrag.dokument.forsendelse.service.dao

import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.utenPrefiks
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.repository.DokumentRepository
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilDokumentDo
import no.nav.bidrag.dokument.forsendelse.service.DokumentBestillingService
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import org.springframework.stereotype.Component

@Component
class DokumentTjeneste(private val dokumentRepository: DokumentRepository, private val dokumentBestillingService: DokumentBestillingService) {
    fun opprettNyttDokument(forsendelse: Forsendelse, forespørsel: OpprettDokumentForespørsel, indeks: Int? = null): Dokument {
        val nyDokument = forespørsel.tilDokumentDo(forsendelse, indeks ?: forsendelse.dokumenter.size)

        return lagreDokument(nyDokument)
    }

    fun opprettNyttDokument(forsendelse: Forsendelse, forespørsel: List<OpprettDokumentForespørsel>): List<Dokument> {
        val nyeDokumenter = forespørsel.mapIndexed { i, it -> it.tilDokumentDo(forsendelse, i) }
        return lagreDokumenter(nyeDokumenter.sortertEtterRekkefølge)
    }

    @Transactional
    fun lagreDokument(dokument: Dokument): Dokument {
        val nyDokument = dokumentRepository.save(dokument)
        bestillDokumentHvisNødvendig(nyDokument)
        return nyDokument
    }

    @Transactional
    fun lagreDokumenter(dokument: List<Dokument>): List<Dokument> {
        val nyDokumenter = dokumentRepository.saveAll(dokument).toList()

        nyDokumenter.forEach { bestillDokumentHvisNødvendig(it) }
        return nyDokumenter
    }

    fun hentDokumenterMedReferanse(dokumentreferanse: String): List<Dokument> {
        return dokumentRepository.hentDokumenterMedDokumentreferanse(dokumentreferanse, dokumentreferanse.utenPrefiks.toLong())
    }

    fun hentDokumenterSomHarStatusBestillingFeilet(): List<Dokument> {
        return dokumentRepository.hentDokumenterSomHarStatusBestillingFeilet()
    }

    private fun bestillDokumentHvisNødvendig(dokument: Dokument) {
        if (dokument.dokumentStatus == DokumentStatus.IKKE_BESTILT) {
            dokumentBestillingService.bestill(dokument.forsendelse.forsendelseId!!, dokument.dokumentreferanse)
        }
    }
}
