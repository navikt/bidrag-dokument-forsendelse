package no.nav.bidrag.dokument.forsendelse.api

import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.TestContainerRunner
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

abstract class KontrollerTestContainerRunner : TestContainerRunner() {
    @LocalServerPort
    private val port = 0

    @Autowired
    lateinit var httpHeaderTestRestTemplate: HttpHeaderTestRestTemplate
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
    }

    protected fun utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel: OpprettForsendelseForespørsel): ResponseEntity<OpprettForsendelseRespons> {
        return httpHeaderTestRestTemplate.exchange(
            rootUri(),
            HttpMethod.POST,
            HttpEntity(opprettForsendelseForespørsel),
            OpprettForsendelseRespons::class.java
        )
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

    protected fun utførHentJournalpost(forsendelseId: String): ResponseEntity<JournalpostResponse> {
        return httpHeaderTestRestTemplate.exchange("${rootUri()}/journal/$forsendelseId", HttpMethod.GET, null, JournalpostResponse::class.java)
    }
}
