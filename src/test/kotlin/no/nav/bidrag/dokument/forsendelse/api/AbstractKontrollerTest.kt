package no.nav.bidrag.dokument.forsendelse.api

import StubUtils
import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import no.nav.bidrag.dokument.forsendelse.BidragTemplateLocal
import no.nav.bidrag.dokument.forsendelse.utils.TestDataManager
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import javax.transaction.Transactional

@ActiveProfiles("test")
@SpringBootTest(classes = [BidragTemplateLocal::class, StubUtils::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@EnableMockOAuth2Server
@Transactional
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

    @AfterEach
    fun cleanupDatabase(){
        testDataManager.slettAlleData()
    }
}