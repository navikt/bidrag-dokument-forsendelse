package no.nav.bidrag.dokument.forsendelse.api

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.StorageOptions
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import mu.KotlinLogging
import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DokumentMetadata
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.TestContainerRunner
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.FerdigstillDokumentRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.hendelse.JournalpostKafkaHendelseProdusent
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
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
    lateinit var httpHeaderTestRestTemplate: HttpHeaderTestRestTemplate

    @MockkBean
    lateinit var forsendelseHendelseProdusent: JournalpostKafkaHendelseProdusent
    protected fun rootUri(): String {
        return "http://localhost:$port/api/forsendelse"
    }

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
        every { forsendelseHendelseProdusent.publiserForsendelse(any()) } returns Unit
    }

    fun initBucket(retryCount: Int = 0) {
        if (retryCount > 3) return
        val storage = StorageOptions.newBuilder()
            .setHost(host)
            .setProjectId("bidrag-local")
            .setCredentials(NoCredentials.getInstance()).build()
        try {
            storage.service.create(BucketInfo.of(bucketNavn))
        } catch (e: Exception) {
            log.error(e) { "Failed while creating bucket. Host = $host" }
            initBucket(retryCount + 1)
        }
    }

    protected fun utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel: OpprettForsendelseForespørsel): ResponseEntity<OpprettForsendelseRespons> {
        return httpHeaderTestRestTemplate.postForEntity<OpprettForsendelseRespons>(
            rootUri(),
            HttpEntity(opprettForsendelseForespørsel)
        )
    }

    protected fun utførOppdaterForsendelseForespørsel(
        forsendelseId: String,
        oppdaterForespørsel: OppdaterForsendelseForespørsel
    ): ResponseEntity<OppdaterForsendelseResponse> {
        return httpHeaderTestRestTemplate.patchForEntity<OppdaterForsendelseResponse>(
            "${rootUri()}/$forsendelseId",
            HttpEntity(oppdaterForespørsel)
        )
    }

    protected fun utførHentForsendelse(forsendelseId: String, saksnummer: String? = null): ResponseEntity<ForsendelseResponsTo> {
        return httpHeaderTestRestTemplate.getForEntity<ForsendelseResponsTo>(
            "${rootUri()}/$forsendelseId${saksnummer?.let { "?saksnummer=$it" }}"
        )
    }

    protected fun utførHentJournalpost(forsendelseId: String): ResponseEntity<JournalpostResponse> {
        return httpHeaderTestRestTemplate.getForEntity<JournalpostResponse>("${rootUri()}/journal/$forsendelseId")
    }

    protected fun utførDistribuerForsendelse(
        forsendelseId: String,
        forespørsel: DistribuerJournalpostRequest? = null,
        batchId: String? = null
    ): ResponseEntity<DistribuerJournalpostResponse> {
        return httpHeaderTestRestTemplate.postForEntity<DistribuerJournalpostResponse>(
            "${rootUri()}/journal/distribuer/$forsendelseId${batchId?.let { "?batchId=$it" }}",
            forespørsel?.let { HttpEntity(it) }
        )
    }

    fun utførFerdigstillDokument(
        forsendelseId: String,
        dokumentreferanse: String,
        request: FerdigstillDokumentRequest
    ): ResponseEntity<DokumentRespons> {
        return httpHeaderTestRestTemplate.patchForEntity<DokumentRespons>(
            "${rootUri()}/redigering/$forsendelseId/$dokumentreferanse/ferdigstill",
            HttpEntity(request)
        )
    }

    fun utførHentDokumentMetadata(
        forsendelseId: String,
        dokumentreferanse: String
    ): ResponseEntity<List<DokumentMetadata>> {
        return httpHeaderTestRestTemplate.optionsForEntity<List<DokumentMetadata>>(
            "${rootUri()}/dokument/$forsendelseId/$dokumentreferanse"
        )
    }

    fun utførHentDokument(
        forsendelseId: String,
        dokumentreferanse: String
    ): ResponseEntity<ByteArray> {
        return httpHeaderTestRestTemplate.getForEntity<ByteArray>(
            "${rootUri()}/dokument/$forsendelseId/$dokumentreferanse"
        )
    }
}
