package no.nav.bidrag.dokument.forsendelse.persistence.bucket

import com.google.api.gax.retrying.RetrySettings
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.NoCredentials
import com.google.cloud.WriteChannel
import com.google.cloud.kms.v1.CryptoKeyName
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.KmsEnvelopeAeadKeyManager
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON
import org.springframework.context.annotation.Scope
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.threeten.bp.Duration
import java.nio.ByteBuffer
import java.util.Optional

private val LOGGER = KotlinLogging.logger {}

@Component
@Scope(SCOPE_SINGLETON)
class GcpCloudStorage(
    @Value("\${BUCKET_NAME}") private val bucketNavn: String,
    @Value("\${GCP_DOCUMENT_CLIENTSIDE_KMS_KEY_PATH}") private val kmsClientsideFilename: String,
    @Value("\${GCP_HOST:#{null}}") private val host: String? = null,
    @Value("\${GCP_CREDENTIALS_PATH:#{null}}") private val credentialsPath: String? = null,
    @Value("\${DISABLE_CLIENTSIDE_ENCRYPTION:false}") private val disableClientsideEncryption: Boolean // Only use when running application locally
) {
    private var keyVersion = -1
    private val retrySetting = RetrySettings.newBuilder()
        .setMaxAttempts(3)
        .setTotalTimeout(Duration.ofMillis(3000)).build()
    private val storage = StorageOptions.newBuilder()
        .setHost(host)
        .setCredentials(if (host != null) NoCredentials.getInstance() else GoogleCredentials.getApplicationDefault())
        .setRetrySettings(retrySetting).build().service

    init {
        AeadConfig.register()
        GcpKmsClient.register(
            Optional.of(kmsClientsideFilename),
            Optional.ofNullable(credentialsPath?.let { ClassPathResource(credentialsPath).file.absolutePath })
        )
        fetchKeyVersion()
    }

    private val tinkClient: Aead = initTinkClient()

    private fun fetchKeyVersion() {
        if (disableClientsideEncryption) return
        KeyManagementServiceClient.create().use { client ->
            val keyName = CryptoKeyName.parse(kmsClientsideFilename.replace("gcp-kms://", ""))
            val key = client.getCryptoKey(keyName)
            keyVersion = key.primary.name.split("cryptoKeyVersions/")[1].toInt()
        }
    }

    private fun initTinkClient(): Aead {
        val handle = KeysetHandle.generateNew(
            KmsEnvelopeAeadKeyManager.createKeyTemplate(kmsClientsideFilename, KeyTemplates.get("AES256_GCM"))
        )
        return handle.getPrimitive(Aead::class.java)
    }

    fun slettFil(filnavn: String) {
        LOGGER.info("Sletter fil $filnavn fra GCP-bucket: $bucketNavn")
        storage.delete(lagBlobinfo(filnavn).blobId)
    }

    fun lagreFil(filnavn: String, byteArrayStream: ByteArray): LagreFilResponse {
        LOGGER.info("Starter overføring av fil: $filnavn til GCP-bucket: $bucketNavn")
        val blobInfo = lagBlobinfo(filnavn)
        val encryptedFile = encryptFile(byteArrayStream, blobInfo)
        getGcpWriter(blobInfo).use { it.write(ByteBuffer.wrap(encryptedFile, 0, encryptedFile.count())) }
        LOGGER.info("Fil: $filnavn har blitt lastet opp til GCP-bucket: $bucketNavn")
        return LagreFilResponse(keyVersion.toString(), blobInfo.blobId.toGsUtilUri())
    }

    fun hentFil(filnavn: String): ByteArray {
        LOGGER.info("Henter fil ${lagBlobinfo(filnavn).blobId} fra bucket $bucketNavn")
        try {
            val blobInfo = lagBlobinfo(filnavn)
            return decryptFile(storage.readAllBytes(blobInfo.blobId), blobInfo)
        } catch (e: StorageException) {
            throw HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Finnes ingen dokumentfil i bucket $bucketNavn med filsti ${lagBlobinfo(filnavn).blobId}"
            )
        }
    }

    private fun getGcpWriter(blobInfo: BlobInfo): WriteChannel {
        return storage.writer(blobInfo, createObjectUploadPrecondition(blobInfo))
    }

    private fun decryptFile(file: ByteArray, blobInfo: BlobInfo): ByteArray {
        if (disableClientsideEncryption) return file
        // Based on example from https://cloud.google.com/kms/docs/client-side-encryption
        LOGGER.info { "Dekryptrerer fil ${blobInfo.name}" }
        val associatedData = blobInfo.blobId.toString().encodeToByteArray()
        return tinkClient.decrypt(file, associatedData)
    }

    private fun encryptFile(file: ByteArray, blobInfo: BlobInfo): ByteArray {
        if (disableClientsideEncryption) return file
        // This will bind the encryption to the location of the GCS blob. That if, if you rename or
        // move the blob to a different bucket, decryption will fail.
        // See https://developers.google.com/tink/aead#associated_data.

        // Based on example from https://cloud.google.com/kms/docs/client-side-encryption
        LOGGER.info { "Kryptrerer fil ${blobInfo.name}" }
        val associatedData = blobInfo.blobId.toString().encodeToByteArray()
        return tinkClient.encrypt(file, associatedData)
    }

    private fun createObjectUploadPrecondition(blobInfo: BlobInfo): Storage.BlobWriteOption {
        return if (storage.get(blobInfo.blobId) == null) {
            Storage.BlobWriteOption.doesNotExist()
        } else {
            Storage.BlobWriteOption.generationMatch(
                storage.get(blobInfo.blobId).generation
            )
        }
    }

    private fun lagBlobinfo(filnavn: String): BlobInfo {
        return BlobInfo.newBuilder(bucketNavn, filnavn)
            .setContentType("application/pdf")
            .build()
    }
}
