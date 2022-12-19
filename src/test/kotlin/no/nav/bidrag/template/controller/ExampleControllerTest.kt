package no.nav.bidrag.template.controller

import StubUtils
import io.kotest.matchers.shouldBe
import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import no.nav.bidrag.template.BidragTemplateLocal
import no.nav.bidrag.template.model.HentPersonResponse
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles


@ActiveProfiles("test")
@SpringBootTest(classes = [BidragTemplateLocal::class, StubUtils::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@EnableMockOAuth2Server
class ExampleControllerTest {

    @LocalServerPort
    private val port = 0

    @Autowired
    lateinit var stubUtils: StubUtils
    @Autowired
    lateinit var httpHeaderTestRestTemplate: HttpHeaderTestRestTemplate

    @Test
    fun `Skal hente persondata`(){
        stubUtils.stubBidragPersonResponse(HentPersonResponse("123213", "Navn Navnesen", "213213213"))
        val response = httpHeaderTestRestTemplate.exchange("${rootUri()}//213213213213",    HttpMethod.GET, null, HentPersonResponse::class.java)

        response.statusCode shouldBe HttpStatus.OK

    }

    fun rootUri(): String{
        return "http://localhost:$port"
    }

}