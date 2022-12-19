import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import org.springframework.http.HttpHeaders
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import no.nav.bidrag.template.model.HentPersonResponse
import org.junit.Assert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component


@Component
class StubUtils {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    companion object {
            open fun aClosedJsonResponse(): ResponseDefinitionBuilder {
                return aResponse()
                    .withHeader(HttpHeaders.CONNECTION, "close")
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            }
    }

    fun stubBidragPersonResponse(personResponse: HentPersonResponse){
        try {
            WireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/person/.*")).willReturn(
                    StubUtils.aClosedJsonResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(ObjectMapper().writeValueAsString(personResponse))
                )
            )
        } catch (e: JsonProcessingException) {
            Assert.fail(e.message)
        }
    }
}