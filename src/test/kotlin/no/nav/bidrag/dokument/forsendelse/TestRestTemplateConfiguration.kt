package no.nav.bidrag.dokument.forsendelse

import com.nimbusds.jose.JOSEObjectType
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.client.RestTemplate

@Configuration
@Profile("test")
@AutoConfigureTestRestTemplate
class TestRestTemplateConfiguration {
    @Autowired
    private lateinit var mockOAuth2Server: MockOAuth2Server

    @Value("\${AZURE_APP_CLIENT_ID}")
    private lateinit var clientId: String

    @Bean
    fun httpHeaderTestRestTemplate(): TestRestTemplate =
        TestRestTemplate(
            RestTemplateBuilder()
                .additionalInterceptors({ request, body, execution ->
                    request.headers.add(HttpHeaders.AUTHORIZATION, generateBearerToken())
                    execution.execute(request, body)
                }),
        )

    //    private fun generateBearerToken(): String {
//        val token = mockOAuth2Server.issueToken("aad", SAKSBEHANDLER_IDENT, clientId)
//        return "Bearer " + token?.serialize()
//    }
    private fun generateBearerToken(): String {
        val iss = mockOAuth2Server.issuerUrl("aad")
        val newIssuer = iss.newBuilder().host("localhost").build()
        val token =
            mockOAuth2Server.issueToken(
                "aad",
                clientId,
                DefaultOAuth2TokenCallback(
                    "aad",
                    SAKSBEHANDLER_IDENT,
                    JOSEObjectType.JWT.type,
                    listOf(clientId),
                    mapOf("iss" to newIssuer.toString()),
                    3600,
                ),
            )
        return "Bearer " + token.serialize()
    }
}
