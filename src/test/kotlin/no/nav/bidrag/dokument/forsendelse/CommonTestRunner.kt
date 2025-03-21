package no.nav.bidrag.dokument.forsendelse

import StubUtils
import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.dokument.forsendelse.utils.TestDataManager
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.core.io.Resource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@ActiveProfiles("test")
@SpringBootTest(
    classes = [BidragDokumentForsendelseTest::class, StubUtils::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureWireMock(port = 0)
@EnableMockOAuth2Server
@ExtendWith(SpringExtension::class)
@Import(AppContext::class)
abstract class CommonTestRunner {
    @Autowired
    lateinit var stubUtils: StubUtils

    @Autowired
    lateinit var testDataManager: TestDataManager

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Value("classpath:__files/kodeverk/kodeverk_kommuner.json")
    private lateinit var kodeverkKommuner: Resource

    @Value("classpath:__files/kodeverk/kodeverk_postnummer.json")
    private lateinit var kodeverkPostnummer: Resource

    @BeforeEach
    fun initMocks() {
        mockKodeverk()
    }

    @AfterEach
    fun reset() {
        resetWiremockServers()
    }

    private fun resetWiremockServers() {
        applicationContext
            .getBeansOfType(WireMockServer::class.java)
            .values
            .forEach(WireMockServer::resetRequests)
    }

    protected fun mockKodeverk() {
        stubUtils.stubUnleash()
        stubUtils.stubKodeverkPostnummerEndepunkt()
    }
}
