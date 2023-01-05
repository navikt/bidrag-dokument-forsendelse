package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.dto.DokumentFormatDto
import no.nav.bidrag.dokument.dto.ÅpneDokumentMetadata
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.model.Dokumentreferanse
import no.nav.bidrag.dokument.forsendelse.model.FantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hent
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hoveddokumentFørst
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.journalpostIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilDokumentStatusDto
import org.springframework.stereotype.Component

@Component
class HentDokumentTjeneste(val forsendelseTjeneste: ForsendelseTjeneste) {


    fun hentDokument(forsendelseId: Long, dokumentreferanse: Dokumentreferanse): ByteArray {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: throw FantIkkeDokument("Fant ikke forsendelse med forsendelseId=$forsendelseId")

        val arkivSystem = forsendelse.dokumenter.hent(dokumentreferanse)?.arkivsystem
        if (arkivSystem == null || arkivSystem != DokumentArkivSystem.BIDRAG){
            throw FantIkkeDokument("Kan ikke hente dokument $dokumentreferanse med forsendelseId $forsendelseId fra arkivsystem = $arkivSystem")
        }

        val dokument = forsendelse.dokumenter.hent(dokumentreferanse)
        // TODO: Hent dokument fra ny løsning
        // TODO: Hent redigert dokument fra bytearray
        return "DOK".toByteArray()
    }

    fun hentDokumentMetadata(forsendelseId: Long, dokumentreferanse: Dokumentreferanse?): List<ÅpneDokumentMetadata> {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: throw FantIkkeDokument("Fant ikke forsendelse med forsendelseId=$forsendelseId")

        if (dokumentreferanse.isNullOrEmpty()){
            return forsendelse.dokumenter.hoveddokumentFørst.map { mapTilÅpneDokumentMetadata(it) }
        }

        val dokument = forsendelse.dokumenter.hent(dokumentreferanse) ?: throw FantIkkeDokument("Fant ikke dokumentreferanse=$dokumentreferanse i forsendelseId=$forsendelseId")

        return listOf(mapTilÅpneDokumentMetadata(dokument))
    }


    private fun mapTilÅpneDokumentMetadata(dokument: Dokument): ÅpneDokumentMetadata{
        if (dokument.arkivsystem == DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER){
            return ÅpneDokumentMetadata(
                journalpostId = dokument.journalpostIdMedPrefix,
                dokumentreferanse = dokument.dokumentreferanse,
                format = when (dokument.dokumentStatus) {
                    DokumentStatus.UNDER_PRODUKSJON, DokumentStatus.UNDER_REDIGERING -> DokumentFormatDto.MBDOK
                    else -> DokumentFormatDto.PDF
                },
                status = dokument.tilDokumentStatusDto()
            )
        }

        return ÅpneDokumentMetadata(
            journalpostId = dokument.journalpostIdMedPrefix,
            dokumentreferanse = dokument.dokumentreferanse,
            format = DokumentFormatDto.PDF,
            status = dokument.tilDokumentStatusDto()
        )
    }
}