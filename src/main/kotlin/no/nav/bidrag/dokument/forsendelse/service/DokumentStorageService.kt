package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import org.springframework.stereotype.Service

@Service
class DokumentStorageService(private val gcpCloudStorage: GcpCloudStorage) {

    private val FOLDER_NAME = "dokumenter"

    fun slettFil(filnavn: String) {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        gcpCloudStorage.slettFil("$FOLDER_NAME/$filenameWithExtension")
    }

    fun lagreFil(filnavn: String, dokument: ByteArray) {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        gcpCloudStorage.lagreFil("$FOLDER_NAME/$filenameWithExtension", dokument)
    }

    fun hentFil(filnavn: String): ByteArray {
        val filenameWithExtension = if (!filnavn.endsWith(".pdf")) "$filnavn.pdf" else filnavn
        return gcpCloudStorage.hentFil("$FOLDER_NAME/$filenameWithExtension")
    }
}
