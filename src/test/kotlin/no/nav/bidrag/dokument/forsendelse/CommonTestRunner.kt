package no.nav.bidrag.dokument.forsendelse

import StubUtils
import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.bidrag.dokument.forsendelse.utils.TestDataManager
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(classes = [BidragDokumentForsendelseTest::class, StubUtils::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@EnableMockOAuth2Server
abstract class CommonTestRunner {

    @Autowired
    lateinit var stubUtils: StubUtils
    @Autowired
    lateinit var testDataManager: TestDataManager

    @Autowired
    private lateinit var applicationContext: ApplicationContext
    @AfterEach
    fun reset() {
        resetWiremockServers()
    }

    private fun resetWiremockServers() {
        applicationContext.getBeansOfType(WireMockServer::class.java)
            .values
            .forEach(WireMockServer::resetRequests)
    }
}