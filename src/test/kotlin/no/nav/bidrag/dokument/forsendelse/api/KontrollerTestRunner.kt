package no.nav.bidrag.dokument.forsendelse.api

import no.nav.bidrag.commons.web.EnhetFilter
import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import no.nav.bidrag.dokument.dto.AvvikType
import no.nav.bidrag.dokument.dto.Avvikshendelse
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.CommonTestRunner
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
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
    lateinit var httpHeaderTestRestTemplate: HttpHeaderTestRestTemplate
    protected fun rootUri(): String {
        return "http://localhost:$port/api/forsendelse"
    }

    @BeforeEach
    fun setupMocks() {
        stubUtils.stubHentPersonSpraak()
        stubUtils.stubHentPerson()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
        stubUtils.stubTilgangskontrollTema()
    }

    @AfterEach
    fun cleanupDatabase() {
        testDataManager.slettAlleData()
    }

    protected fun utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel: OpprettForsendelseForespørsel): ResponseEntity<OpprettForsendelseRespons> {
        return httpHeaderTestRestTemplate.exchange(
            rootUri(),
            HttpMethod.POST,
            HttpEntity(opprettForsendelseForespørsel),
            OpprettForsendelseRespons::class.java
        )
    }

    protected fun utførSlettDokumentForespørsel(forsendelseId: Long, dokumentreferanse: String): ResponseEntity<OppdaterForsendelseResponse> {
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/$forsendelseId/${dokumentreferanse}",
            HttpMethod.DELETE,
            null,
            OppdaterForsendelseResponse::class.java
        )
    }

    protected fun utførLeggTilDokumentForespørsel(
        forsendelseId: Long,
        opprettDokumentForespørsel: OpprettDokumentForespørsel
    ): ResponseEntity<DokumentRespons> {
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/$forsendelseId/dokument",
            HttpMethod.POST,
            HttpEntity(opprettDokumentForespørsel),
            DokumentRespons::class.java
        )
    }

    protected fun utførHentJournalForSaksnummer(saksnummer: String, fagomrader: List<String> = listOf("BID")): ResponseEntity<List<JournalpostDto>> {
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/sak/${saksnummer}/journal?${fagomrader.joinToString("&") { "fagomrade=$it" }}",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<JournalpostDto>>() {})
    }

    protected fun utførOppdaterForsendelseForespørsel(
        forsendelseId: String,
        oppdaterForespørsel: OppdaterForsendelseForespørsel
    ): ResponseEntity<OppdaterForsendelseResponse> {
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/$forsendelseId",
            HttpMethod.PATCH,
            HttpEntity(oppdaterForespørsel),
            OppdaterForsendelseResponse::class.java
        )
    }

    protected fun utførHentJournalpost(forsendelseId: String, saksnummer: String? = null): ResponseEntity<JournalpostResponse> {
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/journal/$forsendelseId${saksnummer?.let { "?saksnummer=$it" }}",
            HttpMethod.GET,
            null,
            JournalpostResponse::class.java
        )
    }

    protected fun utførHentAvvik(forsendelseId: String): ResponseEntity<List<AvvikType>> {
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<AvvikType>>() {})
    }

    protected fun utførAvbrytForsendelseAvvik(forsendelseId: String): ResponseEntity<Void> {
        val headers = HttpHeaders()
        headers.set(EnhetFilter.X_ENHET_HEADER, "4806")
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpMethod.POST,
            HttpEntity(Avvikshendelse(AvvikType.FEILFORE_SAK.name, "4806"), headers),
            Void::class.java
        )
    }

    protected fun utførSlettJournalpostForsendelseAvvik(forsendelseId: String): ResponseEntity<Void> {
        val headers = HttpHeaders()
        headers.set(EnhetFilter.X_ENHET_HEADER, "4806")
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpMethod.POST,
            HttpEntity(Avvikshendelse(AvvikType.SLETT_JOURNALPOST.name, "4806"), headers),
            Void::class.java
        )
    }

    protected fun utførEndreFagområdeForsendelseAvvik(forsendelseId: String, nyFagområde: String): ResponseEntity<Void> {
        val headers = HttpHeaders()
        headers.set(EnhetFilter.X_ENHET_HEADER, "4806")
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpMethod.POST,
            HttpEntity(
                Avvikshendelse(
                    saksnummer = null,
                    adresse = null,
                    dokumenter = emptyList(),
                    beskrivelse = null,
                    avvikType = AvvikType.ENDRE_FAGOMRADE.name,
                    detaljer = mapOf("fagomrade" to nyFagområde, "enhetsnummer" to "4806")
                ), headers
            ),
            Void::class.java
        )
    }

}