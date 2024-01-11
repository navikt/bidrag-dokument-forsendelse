package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.model.DokumentBestillSletting
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.LagreFilResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class DokumentStorageService(
    private val gcpCloudStorage: GcpCloudStorage,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val folderName = "dokumenter"

    fun bestillSletting(
        forsendelseId: Long,
        dokumentreferanse: String,
    ) {
        applicationEventPublisher.publishEvent(DokumentBestillSletting(forsendelseId, dokumentreferanse))
    }

    fun slettFil(filnavn: String) {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        gcpCloudStorage.slettFil("$folderName/$filenameWithExtension")
    }

    fun lagreFil(
        filnavn: String,
        dokument: ByteArray,
    ): LagreFilResponse {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        return gcpCloudStorage.lagreFil("$folderName/$filenameWithExtension", dokument)
    }

    fun hentFil(filnavn: String): ByteArray {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        return gcpCloudStorage.hentFil("$folderName/$filenameWithExtension")
    }

    fun totalStørrelse(forsendelseId: Long): Long {
        return gcpCloudStorage.totalStørrelse(forsendelseId)
    }
}
