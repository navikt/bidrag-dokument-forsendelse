package no.nav.bidrag.dokument.forsendelse.tjeneste

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DokumentFormatDto
import no.nav.bidrag.dokument.dto.DokumentMetadata
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.mapper.tilArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.mapper.tilDokumentStatusDto
import no.nav.bidrag.dokument.forsendelse.model.Dokumentreferanse
import no.nav.bidrag.dokument.forsendelse.model.FantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.journalpostIdMedPrefix
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class FysiskDokumentTjeneste(val forsendelseTjeneste: ForsendelseTjeneste) {


    fun hentDokument(forsendelseId: Long, dokumentreferanse: Dokumentreferanse): ByteArray {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: throw FantIkkeDokument("Fant ikke forsendelse med forsendelseId=$forsendelseId")

        val arkivSystem = forsendelse.dokumenter.hentDokument(dokumentreferanse)?.arkivsystem
        if (arkivSystem == null || arkivSystem != DokumentArkivSystem.BIDRAG){
            throw FantIkkeDokument("Kan ikke hente dokument $dokumentreferanse med forsendelseId $forsendelseId fra arkivsystem = $arkivSystem")
        }

        throw NotImplementedError("")

//        val dokument = forsendelse.dokumenter.hent(dokumentreferanse)
        // TODO: Hent dokument fra ny løsning
        // TODO: Hent redigert dokument fra bytearray
//        return "DOK".toByteArray()
    }

    fun hentDokumentMetadata(forsendelseId: Long, dokumentreferanse: Dokumentreferanse?): List<DokumentMetadata> {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: throw FantIkkeDokument("Fant ikke forsendelse med forsendelseId=$forsendelseId")

        if (dokumentreferanse.isNullOrEmpty()){
            return forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map { mapTilÅpneDokumentMetadata(it) }
        }

        val dokument = forsendelse.dokumenter.hentDokument(dokumentreferanse) ?: throw FantIkkeDokument("Fant ikke dokumentreferanse=$dokumentreferanse i forsendelseId=$forsendelseId")

        return listOf(mapTilÅpneDokumentMetadata(dokument))
    }


    private fun mapTilÅpneDokumentMetadata(dokument: Dokument): DokumentMetadata{
        if (dokument.arkivsystem == DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER){
            return DokumentMetadata(
                journalpostId = dokument.journalpostIdMedPrefix,
                dokumentreferanse = dokument.dokumentreferanse,
                format = when (dokument.dokumentStatus) {
                    DokumentStatus.UNDER_PRODUKSJON, DokumentStatus.UNDER_REDIGERING -> DokumentFormatDto.MBDOK
                    else -> DokumentFormatDto.PDF
                },
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto()
            )
        }

        return DokumentMetadata(
            journalpostId = dokument.journalpostIdMedPrefix,
            dokumentreferanse = dokument.dokumentreferanse,
            format = DokumentFormatDto.PDF,
            status = dokument.tilDokumentStatusDto(),
            arkivsystem = dokument.tilArkivSystemDto()
        )
    }
}