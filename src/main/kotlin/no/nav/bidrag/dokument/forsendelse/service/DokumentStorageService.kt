package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.model.DokumentBestillSletting
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class DokumentStorageService(private val gcpCloudStorage: GcpCloudStorage, private val applicationEventPublisher: ApplicationEventPublisher) {

    private val FOLDER_NAME = "dokumenter"
    fun bestillSletting(forsendelseId: Long, dokumentreferanse: String) {
        applicationEventPublisher.publishEvent(DokumentBestillSletting(forsendelseId, dokumentreferanse))
    }

    fun slettFil(filnavn: String) {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        gcpCloudStorage.slettFil("$FOLDER_NAME/$filenameWithExtension")
    }

    fun lagreFil(filnavn: String, dokument: ByteArray): String {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        return gcpCloudStorage.lagreFil("$FOLDER_NAME/$filenameWithExtension", dokument)
    }

    fun hentFil(filnavn: String): ByteArray {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        return gcpCloudStorage.hentFil("$FOLDER_NAME/$filenameWithExtension")
    }
}
