package no.nav.bidrag.dokument.forsendelse

import StubUtils
import no.nav.bidrag.dokument.forsendelse.utils.TestDataManager
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(classes = [BidragTemplateLocal::class, StubUtils::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@EnableMockOAuth2Server
abstract class CommonTestRunner {

    @Autowired
    lateinit var stubUtils: StubUtils
    @Autowired
    lateinit var testDataManager: TestDataManager
}