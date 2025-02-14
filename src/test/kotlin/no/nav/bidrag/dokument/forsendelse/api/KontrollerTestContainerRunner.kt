package no.nav.bidrag.dokument.forsendelse.api

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.StorageOptions
import com.ninjasquad.springmockk.MockkBean
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.every
import no.nav.bidrag.dokument.forsendelse.TestContainerRunner
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.FerdigstillDokumentRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.hendelse.JournalpostKafkaHendelseProdusent
import no.nav.bidrag.transport.dokument.DistribuerJournalpostRequest
import no.nav.bidrag.transport.dokument.DistribuerJournalpostResponse
import no.nav.bidrag.transport.dokument.DokumentMetadata
import no.nav.bidrag.transport.dokument.JournalpostResponse
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

private val log = KotlinLogging.logger {}

abstract class KontrollerTestContainerRunner : TestContainerRunner() {
    @LocalServerPort
    private val port = 0

    @Value("\${BUCKET_NAME}")
    lateinit var bucketNavn: String

    @Value("\${GCP_HOST:#{null}}")
    private val host: String? = null

    @Autowired
    lateinit var httpHeaderTestRestTemplate: TestRestTemplate

    @MockkBean
    lateinit var journalpostKafkaHendelseProdusent: JournalpostKafkaHendelseProdusent

    protected fun rootUri(): String = "http://localhost:$port/api/forsendelse"

    @BeforeEach
    fun setupMocks() {
        stubUtils.stubHentPerson()
        stubUtils.stubHentPersonSpraak()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollTema()
        stubUtils.stubTilgangskontrollPerson()
        initBucket()
        every { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) } returns Unit
    }

    fun initBucket(retryCount: Int = 0) {
        if (retryCount > 3) return
        val storage =
            StorageOptions
                .newBuilder()
                .setHost(host)
                .setProjectId("bidrag-local")
                .setCredentials(NoCredentials.getInstance())
                .build()
        try {
            storage.service.create(BucketInfo.of(bucketNavn))
        } catch (e: Exception) {
            log.error(e) { "Failed while creating bucket. Host = $host" }
            initBucket(retryCount + 1)
        }
    }

    protected fun utførOpprettForsendelseForespørsel(
        opprettForsendelseForespørsel: OpprettForsendelseForespørsel,
    ): ResponseEntity<OpprettForsendelseRespons> =
        httpHeaderTestRestTemplate.postForEntity<OpprettForsendelseRespons>(
            rootUri(),
            HttpEntity(opprettForsendelseForespørsel),
        )

    protected fun utførOppdaterForsendelseForespørsel(
        forsendelseId: String,
        oppdaterForespørsel: OppdaterForsendelseForespørsel,
    ): ResponseEntity<OppdaterForsendelseResponse> =
        httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/$forsendelseId",
            HttpMethod.PATCH,
            HttpEntity(oppdaterForespørsel),
            OppdaterForsendelseResponse::class.java,
        )

    protected fun utførHentForsendelse(
        forsendelseId: String,
        saksnummer: String? = null,
    ): ResponseEntity<ForsendelseResponsTo> =
        httpHeaderTestRestTemplate.getForEntity<ForsendelseResponsTo>(
            "${rootUri()}/$forsendelseId${saksnummer?.let { "?saksnummer=$it" }}",
        )

    protected fun utførHentJournalpost(forsendelseId: String): ResponseEntity<JournalpostResponse> = httpHeaderTestRestTemplate.getForEntity<JournalpostResponse>("${rootUri()}/journal/$forsendelseId")

    protected fun utførDistribuerForsendelse(
        forsendelseId: String,
        forespørsel: DistribuerJournalpostRequest? = null,
        batchId: String? = null,
    ): ResponseEntity<DistribuerJournalpostResponse> =
        httpHeaderTestRestTemplate.postForEntity<DistribuerJournalpostResponse>(
            "${rootUri()}/journal/distribuer/$forsendelseId${batchId?.let { "?batchId=$it" }}",
            forespørsel?.let { HttpEntity(it) },
        )

    fun utførFerdigstillDokument(
        forsendelseId: String,
        dokumentreferanse: String,
        request: FerdigstillDokumentRequest,
    ): ResponseEntity<DokumentRespons> =
        httpHeaderTestRestTemplate.exchange<DokumentRespons>(
            "${rootUri()}/redigering/$forsendelseId/$dokumentreferanse/ferdigstill",
            HttpMethod.PATCH,
            HttpEntity(request),
            DokumentRespons::class.java,
        )

    fun utførHentDokumentMetadata(
        forsendelseId: String,
        dokumentreferanse: String,
    ): ResponseEntity<List<DokumentMetadata>> =
        httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/dokument/$forsendelseId/$dokumentreferanse",
            HttpMethod.OPTIONS,
            null,
            object : ParameterizedTypeReference<List<DokumentMetadata>>() {},
        )

    fun utførHentDokument(
        forsendelseId: String,
        dokumentreferanse: String,
    ): ResponseEntity<ByteArray> =
        httpHeaderTestRestTemplate.getForEntity<ByteArray>(
            "${rootUri()}/dokument/$forsendelseId/$dokumentreferanse",
        )
}
