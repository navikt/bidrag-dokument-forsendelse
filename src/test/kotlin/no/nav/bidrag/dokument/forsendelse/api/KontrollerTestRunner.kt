package no.nav.bidrag.dokument.forsendelse.api

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.bidrag.commons.web.EnhetFilter
import no.nav.bidrag.dokument.forsendelse.CommonTestRunner
import no.nav.bidrag.dokument.forsendelse.hendelse.DokumentKafkaHendelseProdusent
import no.nav.bidrag.dokument.forsendelse.hendelse.JournalpostKafkaHendelseProdusent
import no.nav.bidrag.transport.dokument.AvvikType
import no.nav.bidrag.transport.dokument.Avvikshendelse
import no.nav.bidrag.transport.dokument.DokumentMetadata
import no.nav.bidrag.transport.dokument.JournalpostDto
import no.nav.bidrag.transport.dokument.JournalpostResponse
import no.nav.bidrag.transport.dokument.forsendelse.DokumentRespons
import no.nav.bidrag.transport.dokument.forsendelse.ForsendelseResponsTo
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterForsendelseForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterForsendelseResponse
import no.nav.bidrag.transport.dokument.forsendelse.OpprettDokumentForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseRespons
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

abstract class KontrollerTestRunner : CommonTestRunner() {
    @LocalServerPort
    private val port = 0

    @Autowired
    lateinit var httpHeaderTestRestTemplate: TestRestTemplate

    @MockkBean
    lateinit var dokumentKafkaHendelseProdusent: DokumentKafkaHendelseProdusent

    @MockkBean
    lateinit var forsendelseHendelseProdusent: JournalpostKafkaHendelseProdusent

    protected fun rootUri(): String = "http://localhost:$port/api/forsendelse"

    @BeforeEach
    fun setupMocks() {
        stubUtils.stubUnleash()
        stubUtils.stubKodeverkPostnummerEndepunkt()
        stubUtils.stubHentPersonSpraak()
        stubUtils.stubHentPerson()
        stubUtils.stubHentSak()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
        stubUtils.stubTilgangskontrollTema()
        every { dokumentKafkaHendelseProdusent.publiser(any()) } returns Unit
        every { forsendelseHendelseProdusent.publiser(any()) } returns Unit
        every { forsendelseHendelseProdusent.publiserForsendelse(any()) } returns Unit
    }

    @AfterEach
    fun cleanupDatabase() {
        testDataManager.slettAlleData()
    }

    protected fun utførOpprettForsendelseForespørsel(
        opprettForsendelseForespørsel: OpprettForsendelseForespørsel,
    ): ResponseEntity<OpprettForsendelseRespons> =
        httpHeaderTestRestTemplate.postForEntity<OpprettForsendelseRespons>(
            rootUri(),
            HttpEntity(opprettForsendelseForespørsel),
        )

    protected fun utførSlettDokumentForespørsel(
        forsendelseId: Long,
        dokumentreferanse: String,
    ): ResponseEntity<OppdaterForsendelseResponse> =
        httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/$forsendelseId/$dokumentreferanse",
            HttpMethod.DELETE,
            null,
            OppdaterForsendelseResponse::class.java,
        )

    protected fun utførLeggTilDokumentForespørsel(
        forsendelseId: Long,
        opprettDokumentForespørsel: OpprettDokumentForespørsel,
    ): ResponseEntity<DokumentRespons> =
        httpHeaderTestRestTemplate.postForEntity<DokumentRespons>(
            "${rootUri()}/$forsendelseId/dokument",
            HttpEntity(opprettDokumentForespørsel),
        )

    protected fun utførHentJournalForSaksnummer(
        saksnummer: String,
        fagomrader: List<String> =
            listOf(
                "BID",
            ),
    ): ResponseEntity<List<JournalpostDto>> =
        httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/sak/$saksnummer/journal?${fagomrader.joinToString("&") { "fagomrade=$it" }}",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<JournalpostDto>>() {},
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

    protected fun utførHentJournalpostMedFeil(
        forsendelseId: String,
        saksnummer: String? = null,
    ): ResponseEntity<Any> =
        httpHeaderTestRestTemplate.getForEntity<Any>(
            "${rootUri()}/journal/$forsendelseId${saksnummer?.let { "?saksnummer=$it" }}",
        )

    protected fun utførHentJournalpost(
        forsendelseId: String,
        saksnummer: String? = null,
    ): ResponseEntity<JournalpostResponse> =
        httpHeaderTestRestTemplate.getForEntity<JournalpostResponse>(
            "${rootUri()}/journal/$forsendelseId${saksnummer?.let { "?saksnummer=$it" }}",
        )

    protected fun utførHentAvvik(forsendelseId: String): ResponseEntity<List<AvvikType>> =
        httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<AvvikType>>() {},
        )

    protected fun utførAvbrytForsendelseAvvik(forsendelseId: String): ResponseEntity<Unit> {
        val headers = HttpHeaders()
        headers.set(EnhetFilter.X_ENHET_HEADER, "4806")
        return httpHeaderTestRestTemplate.postForEntity<Unit>(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpEntity(Avvikshendelse(AvvikType.FEILFORE_SAK.name, "4806"), headers),
        )
    }

    protected fun utførSlettJournalpostForsendelseAvvik(forsendelseId: String): ResponseEntity<Unit> {
        val headers = HttpHeaders()
        headers.set(EnhetFilter.X_ENHET_HEADER, "4806")
        return httpHeaderTestRestTemplate.postForEntity<Unit>(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpEntity(Avvikshendelse(AvvikType.SLETT_JOURNALPOST.name, "4806"), headers),
        )
    }

    protected fun utførEndreFagområdeForsendelseAvvik(
        forsendelseId: String,
        nyFagområde: String,
    ): ResponseEntity<Unit> {
        val headers = HttpHeaders()
        headers.set(EnhetFilter.X_ENHET_HEADER, "4806")
        return httpHeaderTestRestTemplate.postForEntity<Unit>(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpEntity(
                Avvikshendelse(
                    saksnummer = null,
                    adresse = null,
                    dokumenter = emptyList(),
                    beskrivelse = null,
                    avvikType = AvvikType.ENDRE_FAGOMRADE.name,
                    detaljer = mapOf("fagomrade" to nyFagområde, "enhetsnummer" to "4806"),
                ),
                headers,
            ),
        )
    }

    fun utførHentDokumentMetadata(
        forsendelseId: String,
        dokumentreferanse: String? = null,
    ): ResponseEntity<List<DokumentMetadata>> =
        httpHeaderTestRestTemplate.exchange<List<DokumentMetadata>>(
            "${rootUri()}/dokument/$forsendelseId${dokumentreferanse?.let { "/$it" }}",
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
