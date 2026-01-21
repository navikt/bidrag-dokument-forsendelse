package no.nav.bidrag.dokument.forsendelse.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.config.UnleashFeatures
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.mapper.tilArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.mapper.tilBestillingForespørsel
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
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import no.nav.bidrag.transport.dokument.DokumentFormatDto
import no.nav.bidrag.transport.dokument.DokumentMetadata
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

fun Dokument.erStatiskDokument() = arkivsystem == DokumentArkivSystem.BIDRAG && metadata.erStatiskDokument()

fun Dokument.erDokumentFraProduksjon() = arkivsystem == DokumentArkivSystem.BIDRAG && !metadata.erStatiskDokument()

fun Dokument.erRedigerbarHtmlDokument() =
    (arkivsystem == DokumentArkivSystem.BIDRAG && dokumentStatus == DokumentStatus.UNDER_REDIGERING) ||
        (arkivsystem == DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER && UnleashFeatures.REDIGERING_NY_KLIENT.isEnabled)

data class HentDokumentResult(
    val data: ByteArray,
    val mediatype: MediaType = MediaType.APPLICATION_PDF,
)

@Component
class FysiskDokumentService(
    val forsendelseTjeneste: ForsendelseTjeneste,
    val dokumentTjeneste: DokumentTjeneste,
    val bidragDokumentConsumer: BidragDokumentConsumer,
    val bidragDokumentBestillingConsumer: BidragDokumentBestillingConsumer,
    val tilgangskontrollService: TilgangskontrollService,
    val dokumentStorageService: DokumentStorageService,
) {
    fun hentDokument(
        forsendelseId: Long,
        dokumentreferanse: String,
    ): HentDokumentResult {
        val forsendelse =
            forsendelseTjeneste.medForsendelseId(forsendelseId)
                ?: fantIkkeDokument(forsendelseId, dokumentreferanse)

        tilgangskontrollService.sjekkTilgangForsendelse(forsendelse)
        val dokument = forsendelse.dokumenter.hentDokument(dokumentreferanse)!!

        if (dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            return HentDokumentResult(dokumentStorageService.hentFil(dokument.filsti))
        }

        if (dokument.erStatiskDokument()) {
            return HentDokumentResult(hentStatiskDokument(dokument.dokumentmalId!!) ?: fantIkkeDokument(forsendelseId, dokumentreferanse))
        }
        if (dokument.erDokumentFraProduksjon()) {
            return HentDokumentResult(
                bidragDokumentBestillingConsumer.produser(dokument.dokumentmalId!!, dokument.tilBestillingForespørsel())
                    ?: fantIkkeDokument(forsendelseId, dokumentreferanse),
            )
        }
        if (dokument.erRedigerbarHtmlDokument()) {
            return HentDokumentResult(
                bidragDokumentBestillingConsumer.produser(dokument.dokumentmalId!!, dokument.tilBestillingForespørsel(), true)
                    ?: fantIkkeDokument(forsendelseId, dokumentreferanse),
                MediaType.TEXT_HTML,
            )
        }
        val arkivSystem = dokument.arkivsystem
        throw FantIkkeDokument("Kan ikke hente dokument $dokumentreferanse med forsendelseId $forsendelseId fra arkivsystem = $arkivSystem")
    }

    fun hentStatiskDokument(malId: String): ByteArray? = bidragDokumentBestillingConsumer.hentDokument(malId)

    fun hentDokument(dokumentreferanse: String): ByteArray {
        val dokument =
            dokumentTjeneste.hentDokument(dokumentreferanse)
                ?: fantIkkeDokument(-1, dokumentreferanse)
        val forsendelse = dokument.forsendelse

        tilgangskontrollService.sjekkTilgangForsendelse(forsendelse)

        if (dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            return dokumentStorageService.hentFil(dokument.filsti)
        }

        if (dokument.erStatiskDokument()) {
            return hentStatiskDokument(dokument.dokumentmalId!!) ?: fantIkkeDokument(-1, dokumentreferanse)
        }

        val arkivSystem = dokument.arkivsystem
        throw FantIkkeDokument(
            "Kan ikke hente dokument $dokumentreferanse med forsendelseId ${forsendelse.forsendelseId} fra arkivsystem = $arkivSystem",
        )
    }

    fun hentFysiskDokument(dokumentMetadata: DokumentMetadata): ByteArray =
        if (dokumentMetadata.arkivsystem == DokumentArkivSystemDto.BIDRAG) {
            hentDokument(
                dokumentMetadata.journalpostId!!.numerisk,
                dokumentMetadata.dokumentreferanse!!,
            ).data
        } else {
            bidragDokumentConsumer.hentDokument(
                dokumentMetadata.journalpostId,
                dokumentMetadata.dokumentreferanse,
            )!!
        }

    fun hentFysiskDokument(dokument: Dokument): ByteArray {
        return if (dokument.arkivsystem == DokumentArkivSystem.BIDRAG || dokument.dokumentStatus == DokumentStatus.KONTROLLERT) {
            hentDokument(
                dokument.forsendelse.forsendelseId!!,
                dokument.dokumentreferanse,
            ).data
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
                dokument.dokumentreferanseOriginal,
            )!!
        } else {
            bidragDokumentConsumer.hentDokument(
                dokument.forsendelseIdMedPrefix,
                dokument.dokumentreferanse,
            )!!
        }
    }

    fun hentDokumentMetadata(
        forsendelseId: Long,
        dokumentreferanse: String? = null,
    ): List<DokumentMetadata> {
        val forsendelse =
            forsendelseTjeneste.medForsendelseId(forsendelseId)
                ?: fantIkkeForsendelse(forsendelseId)

        if (dokumentreferanse.isNullOrEmpty()) {
            return forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map { mapTilDokumentMetadata(it) }
        }

        val dokument =
            forsendelse.dokumenter.hentDokument(dokumentreferanse)
                ?: throw FantIkkeDokument("Fant ikke dokumentreferanse=$dokumentreferanse i forsendelseId=$forsendelseId")

        if (dokument.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
            log.info {
                "Dokument $dokumentreferanse i forsendelse $forsendelseId og " +
                    "er symlink til dokument ${dokument.dokumentreferanseOriginal} i forsendelse ${dokument.journalpostIdOriginal}"
            }
            return hentDokumentMetadata(dokument.forsendelseId!!, dokument.dokumentreferanseOriginal)
        }

        return listOf(mapTilDokumentMetadata(dokument))
    }

    fun hentDokumentMetadataForReferanse(dokumentreferanse: String): List<DokumentMetadata> {
        val dokument =
            dokumentTjeneste.hentDokument(
                dokumentreferanse,
            ) ?: throw FantIkkeDokument("Fant ikke dokumentreferanse=$dokumentreferanse")

        if (dokument.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
            log.info {
                "Dokument $dokumentreferanse er symlink til dokument ${dokument.dokumentreferanseOriginal} " +
                    "i forsendelse ${dokument.journalpostIdOriginal}"
            }
            return hentDokumentMetadata(dokument.forsendelseId!!, dokument.dokumentreferanseOriginal)
        }

        return listOf(mapTilDokumentMetadata(dokument))
    }

    private fun mapTilDokumentMetadata(dokument: Dokument): DokumentMetadata {
        val dokumentreferanse = if (dokument.erFraAnnenKilde) dokument.dokumentreferanseOriginal else dokument.dokumentreferanse
        return if (dokument.dokumentStatus == DokumentStatus.KONTROLLERT || dokument.arkivsystem == DokumentArkivSystem.BIDRAG) {
            DokumentMetadata(
                journalpostId = dokument.forsendelseIdMedPrefix,
                dokumentreferanse = dokument.dokumentreferanse,
                format = DokumentFormatDto.PDF,
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = DokumentArkivSystemDto.BIDRAG,
            )
        } else if (dokument.arkivsystem == DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER) {
            DokumentMetadata(
                journalpostId = dokument.journalpostId,
                dokumentreferanse = dokumentreferanse,
                format =
                    when (dokument.dokumentStatus) {
                        DokumentStatus.UNDER_PRODUKSJON, DokumentStatus.UNDER_REDIGERING -> DokumentFormatDto.MBDOK
                        else -> DokumentFormatDto.PDF
                    },
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto(),
            )
        } else if (dokument.arkivsystem == DokumentArkivSystem.FORSENDELSE) {
            return hentDokumentMetadata(dokument.forsendelseId!!, dokument.dokumentreferanseOriginal).first()
        } else if (dokument.arkivsystem == DokumentArkivSystem.UKJENT) {
            DokumentMetadata(
                journalpostId = dokument.journalpostId,
                dokumentreferanse = dokumentreferanse,
                format = DokumentFormatDto.MBDOK,
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto(),
            )
        } else {
            DokumentMetadata(
                journalpostId = dokument.journalpostId,
                dokumentreferanse = dokumentreferanse,
                format = DokumentFormatDto.PDF,
                status = dokument.tilDokumentStatusDto(),
                arkivsystem = dokument.tilArkivSystemDto(),
            )
        }
    }
}
