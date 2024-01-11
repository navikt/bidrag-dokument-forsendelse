package no.nav.bidrag.dokument.forsendelse.service.dao

import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.erForsendelse
import no.nav.bidrag.dokument.forsendelse.api.dto.utenPrefiks
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilDokumentDo
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.fjernKontrollTegn
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.model.ugyldigEndringAvForsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.repository.DokumentRepository
import no.nav.bidrag.dokument.forsendelse.service.DokumentBestillingService
import no.nav.bidrag.dokument.forsendelse.utvidelser.exists
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class DokumentTjeneste(
    private val dokumentRepository: DokumentRepository,
    private val dokumentBestillingService: DokumentBestillingService,
    private val forsendelseTjeneste: ForsendelseTjeneste,
) {
    fun opprettNyttDokument(
        forsendelse: Forsendelse,
        forespørsel: OpprettDokumentForespørsel,
        indeks: Int? = null,
    ): Dokument {
        val nyDokument = forespørsel.tilDokumentDoMedOriginalLenketDokument(forsendelse, indeks ?: forsendelse.dokumenter.size)

        return lagreDokument(nyDokument)
    }

    fun opprettDokumentDo(
        forsendelse: Forsendelse,
        forespørsel: OpprettDokumentForespørsel,
        indeks: Int? = null,
    ): Dokument {
        return forespørsel.tilDokumentDoMedOriginalLenketDokument(forsendelse, indeks ?: forsendelse.dokumenter.size)
    }

    fun opprettNyttDokument(
        forsendelse: Forsendelse,
        forespørsel: List<OpprettDokumentForespørsel>,
    ): List<Dokument> {
        val nyeDokumenter = forespørsel.mapIndexed { i, it -> it.tilDokumentDoMedOriginalLenketDokument(forsendelse, i) }
        return lagreDokumenter(nyeDokumenter.sortertEtterRekkefølge)
    }

    @Transactional
    fun lagreDokument(dokument: Dokument): Dokument {
        val nyDokument = dokumentRepository.save(dokument)
        bestillDokumentHvisNødvendig(nyDokument)
        return nyDokument
    }

    @Transactional
    fun lagreDokumenter(dokumenter: List<Dokument>): List<Dokument> {
        val nyDokumenter = dokumentRepository.saveAll(dokumenter).toList()
        nyDokumenter.forEach { bestillDokumentHvisNødvendig(it) }
        return nyDokumenter
    }

    fun hentDokumenterMedReferanse(dokumentreferanse: String): List<Dokument> {
        return dokumentRepository.hentDokumenterMedDokumentreferanse(dokumentreferanse, dokumentreferanse.utenPrefiks.toLong())
    }

    fun hentDokument(dokumentreferanse: String): Dokument? {
        return dokumentRepository.findByDokumentId(dokumentreferanse.utenPrefiks.toLong())
    }

    fun hentDokumenterSomHarStatusBestillingFeilet(): List<Dokument> {
        return dokumentRepository.hentDokumenterSomHarStatusBestillingFeilet()
    }

    fun hentDokumenterSomHarStatusUnderProduksjon(): List<Dokument> {
        return dokumentRepository.hentDokumenterSomHarStatusUnderProduksjon()
    }

    fun hentDokumenterSomErUnderRedigering(
        limit: Int,
        afterDate: LocalDateTime? = null,
        beforeDate: LocalDateTime? = null,
    ): List<Dokument> {
        return dokumentRepository.hentDokumentIkkeFerdigstiltFørDato(
            Pageable.ofSize(limit),
            beforeDate ?: LocalDateTime.now().minusHours(12),
            afterDate ?: LocalDateTime.now().minusMonths(6),
        )
    }

    private fun bestillDokumentHvisNødvendig(dokument: Dokument) {
        if (dokument.dokumentStatus == DokumentStatus.IKKE_BESTILT) {
            dokumentBestillingService.bestill(dokument.forsendelse.forsendelseId!!, dokument.dokumentreferanse)
        }
    }

    fun hentOriginalDokument(dokument: Dokument): Dokument {
        if (dokument.arkivsystem != DokumentArkivSystem.FORSENDELSE) return dokument
        val forsendelse = forsendelseTjeneste.medForsendelseId(dokument.forsendelseId!!) ?: fantIkkeForsendelse(dokument.forsendelseId!!)
        // Dette betyr at dokument er lenket til seg selv som ikke burde skje
        if (dokument.lenkeTilDokumentreferanse == dokument.dokumentreferanse) return dokument
        val referertDokument = forsendelse.dokumenter.hentDokument(dokument.lenkeTilDokumentreferanse)
        if (referertDokument?.erFraAnnenKilde == false ||
            referertDokument?.arkivsystem != DokumentArkivSystem.FORSENDELSE
        ) {
            return referertDokument!!
        }
        return hentOriginalDokument(dokument)
    }

    private fun Dokument.tilOriginalDokument() = hentOriginalDokument(this)

    private fun OpprettDokumentForespørsel.tilDokumentDoMedOriginalLenketDokument(
        forsendelse: Forsendelse,
        indeks: Int,
    ): Dokument {
        val dokumentForsendelse =
            this.journalpostId?.erForsendelse?.let { forsendelseTjeneste.medForsendelseId(this.journalpostId.numerisk) }
                ?: return this.tilDokumentDo(forsendelse, indeks)

        if (this.dokumentreferanse.isNullOrEmpty()) {
            ugyldigEndringAvForsendelse(
                "Dokumentreferanse må settes når en dokument opprettes fra en annen forsendelse. " +
                    "Kan ikke knytte en hel forsendelse til en annen forsendelse.",
            )
        }
        val dokumentLenket = dokumentForsendelse.dokumenter.hentDokument(this.dokumentreferanse)!!.tilOriginalDokument()
        validerKanLeggeTilDokument(dokumentLenket, forsendelse)
        val erFraAnnenKilde = dokumentLenket.erFraAnnenKilde
        return Dokument(
            forsendelse = forsendelse,
            tittel = this.tittel.fjernKontrollTegn().ifEmpty { dokumentLenket.tittel },
            språk = this.språk ?: forsendelse.språk,
            arkivsystem = if (erFraAnnenKilde) dokumentLenket.arkivsystem else DokumentArkivSystem.FORSENDELSE,
            dokumentStatus = if (erFraAnnenKilde) DokumentStatus.MÅ_KONTROLLERES else dokumentLenket.dokumentStatus,
            dokumentreferanseOriginal = if (erFraAnnenKilde) dokumentLenket.dokumentreferanseOriginal else dokumentLenket.dokumentreferanse,
            dokumentDato = this.dokumentDato ?: dokumentLenket.dokumentDato,
            journalpostIdOriginal = if (erFraAnnenKilde) dokumentLenket.journalpostIdOriginal else dokumentLenket.forsendelseId.toString(),
            dokumentmalId = this.dokumentmalId ?: dokumentLenket.dokumentmalId,
            metadata = dokumentLenket.metadata,
            rekkefølgeIndeks = indeks,
        )
    }

    private fun validerKanLeggeTilDokument(
        dokumentLenket: Dokument,
        forsendelse: Forsendelse,
    ) {
        val originalDokumentDelAvSammeForsendelse = dokumentLenket.forsendelse.forsendelseId == forsendelse.forsendelseId
        val dokumentLenkerTilSammeForsendelse = dokumentLenket.journalpostIdOriginal == forsendelse.forsendelseId.toString()
        val harAlleredeLenkeTilSammeDokument = forsendelse.dokumenter.exists(dokumentLenket.dokumentreferanse)
        if (dokumentLenkerTilSammeForsendelse || harAlleredeLenkeTilSammeDokument || originalDokumentDelAvSammeForsendelse) {
            ugyldigEndringAvForsendelse(
                "Dokument med tittel \"${dokumentLenket.tittel}\" er allerede lagt til i forsendelse. " +
                    "Kan ikke legge til samme dokument flere ganger",
            )
        }
    }
}
