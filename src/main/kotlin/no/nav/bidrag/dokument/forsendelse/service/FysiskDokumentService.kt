package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.DokumentFormatDto
import no.nav.bidrag.dokument.dto.DokumentMetadata
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.mapper.tilArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.mapper.tilDokumentStatusDto
import no.nav.bidrag.dokument.forsendelse.model.FantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class FysiskDokumentService(
    val forsendelseTjeneste: ForsendelseTjeneste,
    val dokumentTjeneste: DokumentTjeneste,
    val bidragDokumentConsumer: BidragDokumentConsumer,
    val tilgangskontrollService: TilgangskontrollService,
    val dokumentStorageService: DokumentStorageService
) {

    fun hentDokument(forsendelseId: Long, dokumentreferanse: String): ByteArray {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeDokument(forsendelseId, dokumentreferanse)

        tilgangskontrollService.sjekkTilgangForsendelse(forsendelse)
        val dokument = forsendelse.dokumenter.hentDokument(dokumentreferanse)!!

        if (dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            return dokumentStorageService.hentFil(dokument.filsti)
        }

        val arkivSystem = dokument.arkivsystem
        throw FantIkkeDokument("Kan ikke hente dokument $dokumentreferanse med forsendelseId $forsendelseId fra arkivsystem = $arkivSystem")
    }

    fun hentDokument(dokumentreferanse: String): ByteArray {
        val dokument = dokumentTjeneste.hentDokument(dokumentreferanse)
            ?: fantIkkeDokument(-1, dokumentreferanse)
        val forsendelse = dokument.forsendelse

        tilgangskontrollService.sjekkTilgangForsendelse(forsendelse)

        if (dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            return dokumentStorageService.hentFil(dokument.filsti)
        }

        val arkivSystem = dokument.arkivsystem
        throw FantIkkeDokument("Kan ikke hente dokument $dokumentreferanse med forsendelseId ${forsendelse.forsendelseId} fra arkivsystem = $arkivSystem")
    }

    fun hentFysiskDokument(dokumentMetadata: DokumentMetadata): ByteArray {
        return if (dokumentMetadata.arkivsystem == DokumentArkivSystemDto.BIDRAG) {
            hentDokument(
                dokumentMetadata.journalpostId!!.numerisk,
                dokumentMetadata.dokumentreferanse!!
            )
        } else {
            bidragDokumentConsumer.hentDokument(
                dokumentMetadata.journalpostId,
                dokumentMetadata.dokumentreferanse
            )!!
        }
    }

    fun hentFysiskDokument(dokument: Dokument): ByteArray {
        return if (dokument.arkivsystem == DokumentArkivSystem.BIDRAG || dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            hentDokument(
                dokument.forsendelse.forsendelseId!!,
                dokument.dokumentreferanse
            )
        } else if (dokument.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
            val originalDokument = dokumentTjeneste.hentOriginalDokument(dokument)
            return if (originalDokument.dokumentreferanse == dokument.dokumentreferanse) {
                // Hindre stack-overflow hvis det ved feil har blitt lagret lenket dokument som peker til samme forsendelse
                hentFysiskDokument(originalDokument.copy(arkivsystem = DokumentArkivSystem.UKJENT))
            } else {
                hentFysiskDokument(originalDokument)
            }
        } else if (dokument.erFraAnnenKilde) {
            bidragDokumentConsumer.hentDokument(
                dokument.journalpostId,
                dokument.dokumentreferanseOriginal
            )!!
        } else {
            bidragDokumentConsumer.hentDokument(
                dokument.forsendelseIdMedPrefix,
                dokument.dokumentreferanse
            )!!
        }
    }

    fun hentDokumentMetadata(forsendelseId: Long, dokumentreferanse: String? = null): List<DokumentMetadata> {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)

        if (dokumentreferanse.isNullOrEmpty()) {
            return forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map { mapTilDokumentMetadata(it) }
        }

        val dokument = forsendelse.dokumenter.hentDokument(dokumentreferanse)
            ?: throw FantIkkeDokument("Fant ikke dokumentreferanse=$dokumentreferanse i forsendelseId=$forsendelseId")

        if (dokument.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
            log.info { "Dokument $dokumentreferanse i forsendelse $forsendelseId og er symlink til dokument ${dokument.dokumentreferanseOriginal} i forsendelse ${dokument.journalpostIdOriginal}" }
            return hentDokumentMetadata(dokument.forsendelseId!!, dokument.dokumentreferanseOriginal)
        }

        return listOf(mapTilDokumentMetadata(dokument))
    }

    fun hentDokumentMetadataForReferanse(dokumentreferanse: String): List<DokumentMetadata> {
        val dokument = dokumentTjeneste.hentDokument(dokumentreferanse) ?: throw FantIkkeDokument("Fant ikke dokumentreferanse=$dokumentreferanse")

        if (dokument.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
            log.info { "Dokument $dokumentreferanse er symlink til dokument ${dokument.dokumentreferanseOriginal} i forsendelse ${dokument.journalpostIdOriginal}" }
            return hentDokumentMetadata(dokument.forsendelseId!!, dokument.dokumentreferanseOriginal)
        }

        return listOf(mapTilDokumentMetadata(dokument))
    }

    private fun mapTilDokumentMetadata(dokument: Dokument): DokumentMetadata {
        val dokumentreferanse = if (dokument.erFraAnnenKilde) dokument.dokumentreferanseOriginal else dokument.dokumentreferanse
        return if (dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            DokumentMetadata(
                journalpostId = dokument.forsendelseIdMedPrefix,
                dokumentreferanse = dokument.dokumentreferanse,
                format = DokumentFormatDto.PDF,
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = DokumentArkivSystemDto.BIDRAG
            )
        } else if (dokument.arkivsystem == DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER) {
            DokumentMetadata(
                journalpostId = dokument.journalpostId,
                dokumentreferanse = dokumentreferanse,
                format = when (dokument.dokumentStatus) {
                    DokumentStatus.UNDER_PRODUKSJON, DokumentStatus.UNDER_REDIGERING -> DokumentFormatDto.MBDOK
                    else -> DokumentFormatDto.PDF
                },
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto()
            )
        } else if (dokument.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
            return hentDokumentMetadata(dokument.forsendelseId!!, dokument.dokumentreferanseOriginal).first()
        } else if (dokument.arkivsystem == DokumentArkivSystem.UKJENT) {
            DokumentMetadata(
                journalpostId = dokument.journalpostId,
                dokumentreferanse = dokumentreferanse,
                format = DokumentFormatDto.MBDOK,
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto()
            )
        } else {
            DokumentMetadata(
                journalpostId = dokument.journalpostId,
                dokumentreferanse = dokumentreferanse,
                format = DokumentFormatDto.PDF,
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto()
            )
        }
    }
}
