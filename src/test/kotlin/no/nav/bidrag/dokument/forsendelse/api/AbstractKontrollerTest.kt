package no.nav.bidrag.dokument.forsendelse.api

import StubUtils
import io.kotest.matchers.shouldBe
import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import no.nav.bidrag.dokument.forsendelse.BidragTemplateLocal
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.utils.TestDataManager
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(classes = [BidragTemplateLocal::class, StubUtils::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@EnableMockOAuth2Server
abstract class AbstractKontrollerTest {
    @LocalServerPort
    private val port = 0

    @Autowired
    lateinit var stubUtils: StubUtils
    @Autowired
    lateinit var testDataManager: TestDataManager
    @Autowired
    lateinit var httpHeaderTestRestTemplate: HttpHeaderTestRestTemplate
    protected fun rootUri(): String{
        return "http://localhost:$port/api/forsendelse"
    }

    @BeforeEach
    fun setupMocks(){
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
    }

    @AfterEach
    fun cleanupDatabase(){
        testDataManager.slettAlleData()
    }

    protected fun utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel: OpprettForsendelseForespørsel): ResponseEntity<OpprettForsendelseRespons> {
        return httpHeaderTestRestTemplate.exchange(rootUri(), HttpMethod.POST, HttpEntity(opprettForsendelseForespørsel), OpprettForsendelseRespons::class.java)
    }

    protected fun utførSlettDokumentForespørsel(forsendelseId: Long, dokumentreferanse: String): ResponseEntity<OppdaterForsendelseResponse> {
        return httpHeaderTestRestTemplate.exchange("${rootUri()}/$forsendelseId/${dokumentreferanse}", HttpMethod.DELETE, null, OppdaterForsendelseResponse::class.java)

    }
}