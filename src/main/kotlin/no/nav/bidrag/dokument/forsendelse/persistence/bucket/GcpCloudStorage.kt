package no.nav.bidrag.dokument.forsendelse.persistence.bucket

import com.google.api.gax.retrying.RetrySettings
import com.google.cloud.WriteChannel
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.threeten.bp.Duration
import java.nio.ByteBuffer

private val LOGGER = KotlinLogging.logger {}

@Component
class GcpCloudStorage(
    @Value("\${BUCKET_NAME}") private val bucketNavn: String,
    @Value("\${GCP_BUCKET_DOCUMENT_KMS_KEY_PATH}") private val kmsKeyPath: String,
    @Value("\${GCP_HOST:#{null}}") private val host: String? = null
) {

    private val retrySetting = RetrySettings.newBuilder().setTotalTimeout(Duration.ofMillis(3000)).build()
    private val storage = StorageOptions.newBuilder()
        .setHost(host)
        .setRetrySettings(retrySetting).build().service

    fun slettFil(filnavn: String) {
        LOGGER.info("Sletter fil $filnavn fra GCP-bucket: $bucketNavn")
        storage.delete(lagBlobinfo(filnavn).blobId)
    }

    fun lagreFil(filnavn: String, byteArrayStream: ByteArray) {
        LOGGER.info("Starter overføring av fil: $filnavn til GCP-bucket: $bucketNavn")
        hentWriteChannel(filnavn).use { it.write(ByteBuffer.wrap(byteArrayStream, 0, byteArrayStream.count())) }
        LOGGER.info("Fil: $filnavn har blitt lastet opp til GCP-bucket: $bucketNavn")
    }

    fun hentFil(filnavn: String): ByteArray {
        LOGGER.info("Henter fil ${lagBlobinfo(filnavn).blobId} fra bucket $bucketNavn")
        try {
            return storage.readAllBytes(lagBlobinfo(filnavn).blobId)
        } catch (e: StorageException) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Finnes ingen dokumentfil i bucket $bucketNavn med filnavn ${lagBlobinfo(filnavn).blobId}"
            )
        }
    }

    private fun hentWriteChannel(filnavn: String): WriteChannel {
        return storage.writer(lagBlobinfo(filnavn), createObjectUploadPrecondition(filnavn))
    }

    private fun createObjectUploadPrecondition(filnavn: String): Storage.BlobWriteOption {
        return if (storage.get(lagBlobinfo(filnavn).blobId) == null) Storage.BlobWriteOption.doesNotExist()
        else Storage.BlobWriteOption.generationMatch(
            storage.get(lagBlobinfo(filnavn).blobId).generation
        )
    }

    private fun lagBlobinfo(filnavn: String): BlobInfo {
        return BlobInfo.newBuilder(bucketNavn, filnavn)
            .setContentType("application/pdf")
            .build()
    }
}