import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentBestillingResponse
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.HentPersonResponse
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.SaksbehandlerInfoResponse
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_NAVN
import org.junit.Assert
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus


class StubUtils {

    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

    companion object {
            fun aClosedJsonResponse(): ResponseDefinitionBuilder {
                return aResponse()
                    .withHeader(HttpHeaders.CONNECTION, "close")
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            }
    }

    fun stubHentSaksbehandler(){
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/organisasjon/saksbehandler/info/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(SaksbehandlerInfoResponse(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_NAVN)))
            )
        )
    }

    fun stubBestillDokumenDetaljer(){
        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/bestilling/dokumentmal/detaljer")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(
                        mapOf(
                            DOKUMENTMAL_NOTAT to DokumentMalDetaljer("Notat", DokumentMalType.NOTAT),
                            DOKUMENTMAL_UTGÅENDE to DokumentMalDetaljer("Notat", DokumentMalType.UTGÅENDE)
                        )
                    ))
            )
        )
    }

    fun stubBestillDokument(arkivSystem: DokumentArkivSystemDto = DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER, status: HttpStatus = HttpStatus.OK){
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/bestilling/bestill/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(jsonToString(DokumentBestillingResponse(journalpostId = "JOARK-123123", dokumentId = "123213", arkivSystem = arkivSystem)))
            )
        )
    }

    fun stubBestillDokumentFeiler(){
        stubBestillDokument(status = HttpStatus.BAD_REQUEST)
    }

    fun stubOpprettJournalpost(nyJournalpostId: String){
        WireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/dokument/journalpost/JOARK")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(OpprettJournalpostResponse(nyJournalpostId)))
            )
        )
    }


    fun stubBidragPersonResponse(personResponse: HentPersonResponse){
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/person/.*")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(personResponse))
            )
        )
    }

    private fun jsonToString(data: Any): String {
        return try {
            objectMapper.writeValueAsString(data)
        } catch (e: JsonProcessingException) {
            Assert.fail(e.message)
            ""
        }
    }
}