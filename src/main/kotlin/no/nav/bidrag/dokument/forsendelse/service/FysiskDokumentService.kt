package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DokumentFormatDto
import no.nav.bidrag.dokument.dto.DokumentMetadata
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.mapper.tilArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.mapper.tilDokumentStatusDto
import no.nav.bidrag.dokument.forsendelse.model.FantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class FysiskDokumentService(
    val forsendelseTjeneste: ForsendelseTjeneste,
    val bidragDokumentConsumer: BidragDokumentConsumer,
    val dokumentStorageService: DokumentStorageService
) {

    fun hentDokument(forsendelseId: Long, dokumentreferanse: String): ByteArray {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeDokument(forsendelseId, dokumentreferanse)

        val dokument = forsendelse.dokumenter.hentDokument(dokumentreferanse)!!

        if (dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            return dokumentStorageService.hentFil(dokument.filnavn)
        }

        val arkivSystem = dokument.arkivsystem
        throw FantIkkeDokument("Kan ikke hente dokument $dokumentreferanse med forsendelseId $forsendelseId fra arkivsystem = $arkivSystem")
    }

    fun hentFysiskDokument(dokument: Dokument): ByteArray {
        val dokumentreferanse = if (dokument.erFraAnnenKilde) dokument.dokumentreferanseOriginal else dokument.dokumentreferanse

        return if (dokument.erFraAnnenKilde && dokument.dokumentStatus == DokumentStatus.FERDIGSTILT) {
            bidragDokumentConsumer.hentDokument(
                dokument.journalpostId!!,
                dokument.dokumentreferanseOriginal
            )!!
        } else if (dokument.arkivsystem == DokumentArkivSystem.BIDRAG || dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            hentDokument(
                dokument.forsendelse.forsendelseId!!,
                dokument.dokumentreferanse
            )
        } else {
            bidragDokumentConsumer.hentDokument(
                dokument.forsendelseIdMedPrefix,
                dokument.dokumentreferanse
            )!!
        }

    }

    fun hentDokumentMetadata(forsendelseId: Long, dokumentreferanse: String?): List<DokumentMetadata> {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: throw FantIkkeDokument("Fant ikke forsendelse med forsendelseId=$forsendelseId")

        if (dokumentreferanse.isNullOrEmpty()) {
            return forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map { mapTilDokumentMetadata(it) }
        }

        val dokument = forsendelse.dokumenter.hentDokument(dokumentreferanse)
            ?: throw FantIkkeDokument("Fant ikke dokumentreferanse=$dokumentreferanse i forsendelseId=$forsendelseId")

        return listOf(mapTilDokumentMetadata(dokument))
    }

    private fun mapTilDokumentMetadata(dokument: Dokument): DokumentMetadata {
        val dokumentreferanse = if (dokument.erFraAnnenKilde) dokument.dokumentreferanseOriginal else dokument.dokumentreferanse
        if (dokument.arkivsystem == DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER) {
            return DokumentMetadata(
                journalpostId = dokument.journalpostId,
                dokumentreferanse = dokumentreferanse,
                format = when (dokument.dokumentStatus) {
                    DokumentStatus.UNDER_PRODUKSJON, DokumentStatus.UNDER_REDIGERING -> DokumentFormatDto.MBDOK
                    else -> DokumentFormatDto.PDF
                },
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto()
            )
        }

        if (dokument.arkivsystem == DokumentArkivSystem.UKJENT) {
            return DokumentMetadata(
                journalpostId = dokument.journalpostId,
                dokumentreferanse = dokumentreferanse,
                format = DokumentFormatDto.MBDOK,
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto()
            )
        }

        return DokumentMetadata(
            journalpostId = dokument.journalpostId,
            dokumentreferanse = dokumentreferanse,
            format = DokumentFormatDto.PDF,
            status = dokument.tilDokumentStatusDto(),
            arkivsystem = dokument.tilArkivSystemDto()
        )
    }
}
