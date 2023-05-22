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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
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
        return httpHeaderTestRestTemplate.postForEntity<OpprettForsendelseRespons>(
            rootUri(),
            HttpEntity(opprettForsendelseForespørsel)
        )
    }

    protected fun utførSlettDokumentForespørsel(forsendelseId: Long, dokumentreferanse: String): ResponseEntity<OppdaterForsendelseResponse> {
        return httpHeaderTestRestTemplate.delete<OppdaterForsendelseResponse>(
            "${rootUri()}/$forsendelseId/$dokumentreferanse"
        )
    }

    protected fun utførLeggTilDokumentForespørsel(
        forsendelseId: Long,
        opprettDokumentForespørsel: OpprettDokumentForespørsel
    ): ResponseEntity<DokumentRespons> {
        return httpHeaderTestRestTemplate.postForEntity<DokumentRespons>(
            "${rootUri()}/$forsendelseId/dokument",
            HttpEntity(opprettDokumentForespørsel)
        )
    }

    protected fun utførHentJournalForSaksnummer(saksnummer: String, fagomrader: List<String> = listOf("BID")): ResponseEntity<List<JournalpostDto>> {
        return httpHeaderTestRestTemplate.getForEntity<List<JournalpostDto>>(
            "${rootUri()}/sak/$saksnummer/journal?${fagomrader.joinToString("&") { "fagomrade=$it" }}"
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

    protected fun utførHentJournalpost(forsendelseId: String, saksnummer: String? = null): ResponseEntity<JournalpostResponse> {
        return httpHeaderTestRestTemplate.getForEntity<JournalpostResponse>(
            "${rootUri()}/journal/$forsendelseId${saksnummer?.let { "?saksnummer=$it" }}"
        )
    }

    protected fun utførHentAvvik(forsendelseId: String): ResponseEntity<List<AvvikType>> {
        return httpHeaderTestRestTemplate.getForEntity<List<AvvikType>>(
            "${rootUri()}/journal/$forsendelseId/avvik"
        )
    }

    protected fun utførAvbrytForsendelseAvvik(forsendelseId: String): ResponseEntity<Unit> {
        val headers = HttpHeaders()
        headers.set(EnhetFilter.X_ENHET_HEADER, "4806")
        return httpHeaderTestRestTemplate.postForEntity<Unit>(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpEntity(Avvikshendelse(AvvikType.FEILFORE_SAK.name, "4806"), headers)
        )
    }

    protected fun utførSlettJournalpostForsendelseAvvik(forsendelseId: String): ResponseEntity<Unit> {
        val headers = HttpHeaders()
        headers.set(EnhetFilter.X_ENHET_HEADER, "4806")
        return httpHeaderTestRestTemplate.postForEntity<Unit>(
            "${rootUri()}/journal/$forsendelseId/avvik",
            HttpEntity(Avvikshendelse(AvvikType.SLETT_JOURNALPOST.name, "4806"), headers)
        )
    }

    protected fun utførEndreFagområdeForsendelseAvvik(forsendelseId: String, nyFagområde: String): ResponseEntity<Unit> {
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
                    detaljer = mapOf("fagomrade" to nyFagområde, "enhetsnummer" to "4806")
                ),
                headers
            )
        )
    }
}
