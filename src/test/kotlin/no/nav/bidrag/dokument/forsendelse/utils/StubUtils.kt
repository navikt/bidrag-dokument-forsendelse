import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentBestillingResponse
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.SaksbehandlerInfoResponse
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENT_FIL
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettJournalpostResponse
import org.junit.Assert
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.util.Arrays


class StubUtils {

    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

    companion object {
            fun aClosedJsonResponse(): ResponseDefinitionBuilder {
                return aResponse()
                    .withHeader(HttpHeaders.CONNECTION, "close")
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            }
    }

    fun stubHentDokument(){
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/dokument/dokument/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(DOKUMENT_FIL)
            )
        )
    }

    fun stubOpprettJournalpost(nyJournalpostId: String, dokumenter: List<OpprettDokumentDto> = emptyList(), status: HttpStatus = HttpStatus.OK){
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/dokument/journalpost/JOARK")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(jsonToString(nyOpprettJournalpostResponse(nyJournalpostId, dokumenter)))
            )
        )
    }

    fun stubBestillDistribusjon(bestillingId: String){
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/dokument/journal/distribuer/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(DistribuerJournalpostResponse("324324234", bestillingId)))
            )
        )
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
                            DOKUMENTMAL_UTGÅENDE to DokumentMalDetaljer("Utgående", DokumentMalType.UTGÅENDE)
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

    fun stubOpprettJournalpost(nyJournalpostId: String, dokumenter: List<OpprettDokumentDto> = emptyList()){
        WireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/dokument/journalpost/JOARK")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(OpprettJournalpostResponse(nyJournalpostId, dokumenter)))
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

    inner class Valider {
        fun hentDokumentKalt(journalpostId: String, dokumentreferanse: String) {
            val verify = WireMock.getRequestedFor(
                WireMock.urlMatching("/dokument/dokument/$journalpostId/$dokumentreferanse(.*)")
            )
            WireMock.verify(verify)
        }

        fun bestillDistribusjonKaltMed(journalpostId: String, vararg contains: String){
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/dokument/journal/distribuer/$journalpostId")
            )
            verifyContains(verify, *contains)
        }

        fun bestillDokumentKaltMed(dokumentmal: String, vararg contains: String){
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/bestilling/bestill/$dokumentmal")
            )
            verifyContains(verify, *contains)
        }

        fun opprettJournalpostKaltMed(vararg contains: String) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/dokument/journalpost/JOARK")
            )
            verifyContains(verify, *contains)
        }

        private fun verifyContains(verify: RequestPatternBuilder, vararg contains: String){
            Arrays.stream(contains).forEach { verify.withRequestBody(ContainsPattern(it)) }
            WireMock.verify(verify)
        }
    }
}