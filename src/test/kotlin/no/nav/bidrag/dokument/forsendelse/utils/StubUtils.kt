import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import no.nav.bidrag.behandling.felles.dto.vedtak.VedtakDto
import no.nav.bidrag.dokument.dto.AvvikType
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DistribusjonInfoDto
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.DokumentMetadata
import no.nav.bidrag.dokument.dto.JournalpostStatus
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.consumer.dto.BehandlingDto
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentBestillingResponse
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.consumer.dto.SaksbehandlerInfoResponse
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_2
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_3
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENT_FIL
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettDokumentMetadataListe
import no.nav.bidrag.dokument.forsendelse.utils.opprettSak
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.domain.ident.PersonIdent
import no.nav.bidrag.domain.string.FulltNavn
import no.nav.bidrag.transport.person.PersonDto
import org.junit.Assert
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.nio.charset.StandardCharsets
import java.util.Arrays

class StubUtils {

    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

    companion object {
        fun getDokumentMalDetaljerResponse(): MutableMap<String, DokumentMalDetaljer> {
            val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()
            val maldetaljerInputStream = ClassPathResource("__files/maldetaljer/maldetaljer.json").inputStream
            val text = String(maldetaljerInputStream.readAllBytes(), StandardCharsets.UTF_8)
            val stringType = objectMapper.typeFactory.constructType(String::class.java)
            val mapType = objectMapper.typeFactory.constructType(DokumentMalDetaljer::class.java)
            return objectMapper.readValue(
                text,
                objectMapper.typeFactory.constructMapType(
                    MutableMap::class.java,
                    stringType,
                    mapType
                )
            )
        }

        fun aClosedJsonResponse(): ResponseDefinitionBuilder {
            return aResponse()
                .withHeader(HttpHeaders.CONNECTION, "close")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        }
    }

    fun stubBehandling(behandlingDto: BehandlingDto = opprettBehandlingDto()) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/behandling/api/behandling(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(
                        jsonToString(behandlingDto)
                    )
            )
        )
    }

    fun stubVedtak(vedtakDto: VedtakDto = opprettVedtakDto()) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/vedtak/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(
                        jsonToString(vedtakDto)
                    )
            )
        )
    }

    fun stubHentPerson(
        fnr: String? = null,
        personResponse: PersonDto = PersonDto(PersonIdent(MOTTAKER_IDENT), FulltNavn(MOTTAKER_NAVN))
    ) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/person/informasjon"))
                .withRequestBody(if (fnr.isNullOrEmpty()) AnythingPattern() else ContainsPattern(fnr)).willReturn(
                    aClosedJsonResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(jsonToString(personResponse))
                )
        )
    }

    fun stubHentPersonSpraak(result: String = "nb") {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/person/spraak")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(result)
            )
        )
    }

    fun stubTilgangskontrollSak(result: Boolean = true, status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/tilgangskontroll/api/tilgang/sak")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(result.toString())
            )
        )
    }

    fun stubTilgangskontrollPerson(result: Boolean = true, status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/tilgangskontroll/api/tilgang/person")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(result.toString())
            )
        )
    }

    fun stubTilgangskontrollTema(result: Boolean = true, status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/tilgangskontroll/api/tilgang/tema")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(result.toString())
            )
        )
    }

    fun stubHentDokumentMetadata(
        journalpostId: String? = null,
        dokumentreferanse: String? = null,
        response: List<DokumentMetadata> = opprettDokumentMetadataListe("123123123")
    ) {
        var urlMatch = WireMock.urlMatching("/dokument/dokument/(.*)")
        if (journalpostId.isNotNullOrEmpty()) {
            urlMatch = WireMock.urlMatching("/dokument/dokument/$journalpostId${dokumentreferanse?.let { "/$it" } ?: ""}")
        }

        WireMock.stubFor(
            WireMock.options(urlMatch).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(response))
            )
        )
    }

    fun stubHentDokumentFraPDF() {
        val inputstream = ClassPathResource("__files/dokument/test_dokument.pdf").inputStream
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/dokument/dokument/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(inputstream.readAllBytes())
            )
        )
    }

    fun stubHentDokument() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/dokument/dokument/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(DOKUMENT_FIL)
            )
        )
    }

    fun stubHentSak() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/sak/sak/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(opprettSak()))
            )
        )
    }

    fun stubOpprettJournalpost(nyJournalpostId: String, dokumenter: List<OpprettDokumentDto> = emptyList(), status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/dokument/journalpost/JOARK")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(jsonToString(nyOpprettJournalpostResponse(nyJournalpostId, dokumenter)))
            )
        )
    }

    fun stubHentDistribusjonInfo(journalpostId: String? = null, kanal: String? = null, status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/dokument/journal/distribuer/info/${journalpostId ?: "(.*)"}")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(jsonToString(DistribusjonInfoDto(JournalpostStatus.EKSPEDERT, kanal ?: "NAV_NO")))
            )
        )
    }

    fun stubHentDistribusjonInfo(journalpostId: String? = null, distInfo: DistribusjonInfoDto, status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/dokument/journal/distribuer/info/${journalpostId ?: "(.*)"}")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(jsonToString(distInfo))
            )
        )
    }

    fun stubBestillDistribusjon(bestillingId: String, journalpostId: String = "324324234") {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/dokument/journal/distribuer/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(DistribuerJournalpostResponse(journalpostId, bestillingId)))
            )
        )
    }

    fun stubHentSaksbehandler() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/organisasjon/saksbehandler/info/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(SaksbehandlerInfoResponse(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_NAVN)))
            )
        )
    }

    fun stubBestillDokumenDetaljer() {
        val dokumentMalDetaljerMap: MutableMap<String, DokumentMalDetaljer> = getDokumentMalDetaljerResponse()
        dokumentMalDetaljerMap[DOKUMENTMAL_NOTAT] = DokumentMalDetaljer("Notat", DokumentMalType.NOTAT, true)
        dokumentMalDetaljerMap[DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES] = DokumentMalDetaljer("Utgående", DokumentMalType.UTGÅENDE, false)
        dokumentMalDetaljerMap[DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2] = DokumentMalDetaljer("Utgående", DokumentMalType.UTGÅENDE, false)
        dokumentMalDetaljerMap[DOKUMENTMAL_UTGÅENDE] = DokumentMalDetaljer("Utgående", DokumentMalType.UTGÅENDE, true)
        dokumentMalDetaljerMap[DOKUMENTMAL_UTGÅENDE_2] = DokumentMalDetaljer("Utgående", DokumentMalType.UTGÅENDE, true)
        dokumentMalDetaljerMap[DOKUMENTMAL_UTGÅENDE_3] = DokumentMalDetaljer("Utgående", DokumentMalType.UTGÅENDE, true)
        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/bestilling/dokumentmal/detaljer")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(dokumentMalDetaljerMap))
            )
        )
    }

    fun stubBestillDokument(arkivSystem: DokumentArkivSystemDto = DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER, status: HttpStatus = HttpStatus.OK) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching("/bestilling/bestill/(.*)")).willReturn(
                aClosedJsonResponse()
                    .withStatus(status.value())
                    .withBody(
                        jsonToString(
                            DokumentBestillingResponse(
                                journalpostId = "JOARK-123123",
                                dokumentId = "123213",
                                arkivSystem = arkivSystem
                            )
                        )
                    )
            )
        )
    }

    fun stubBestillDokumentFeiler() {
        stubBestillDokument(status = HttpStatus.BAD_REQUEST)
    }

    fun stubOpprettJournalpost(nyJournalpostId: String, dokumenter: List<OpprettDokumentDto> = emptyList()) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/dokument/journalpost/JOARK")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(jsonToString(OpprettJournalpostResponse(nyJournalpostId, dokumenter)))
            )
        )
    }

    fun stubMarkerSomIngenDistribusjon(journalpostId: String) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/dokument/journal/$journalpostId/avvik")).willReturn(
                aClosedJsonResponse()
                    .withStatus(HttpStatus.OK.value())
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
        fun hentVedtakKalt(vedtakId: String, antallGanger: Int = 1) {
            val verify = WireMock.getRequestedFor(
                WireMock.urlMatching("/vedtak/vedtak/$vedtakId")
            )
            WireMock.verify(antallGanger, verify)
        }

        fun markSomIngenDistribusjonUtført(journalpostId: String) {
            val verify = WireMock.getRequestedFor(
                WireMock.urlMatching("/dokument/journal/$journalpostId/avvik")
            )
            verify.withRequestBody(ContainsPattern(AvvikType.MANGLER_ADRESSE.name))
        }

        fun hentBehandlingKalt(behandlingId: String, antallGanger: Int = 1) {
            val verify = WireMock.getRequestedFor(
                WireMock.urlMatching("/behandling/api/behandling/$behandlingId")
            )
            WireMock.verify(antallGanger, verify)
        }

        fun hentDokumentKalt(journalpostId: String, dokumentreferanse: String, antallGanger: Int = 1) {
            val verify = WireMock.getRequestedFor(
                WireMock.urlMatching("/dokument/dokument/$journalpostId/$dokumentreferanse(.*)")
            )
            WireMock.verify(antallGanger, verify)
        }

        fun hentDokumentIkkeKalt() {
            val verify = WireMock.getRequestedFor(
                WireMock.urlMatching("/dokument/dokument/(.*)")
            )
            WireMock.verify(0, verify)
        }

        fun hentDokumentMetadataKalt(journalpostId: String, dokumentreferanse: String? = null, antallGanger: Int = 1) {
            val verify = WireMock.optionsRequestedFor(
                WireMock.urlMatching("/dokument/dokument/$journalpostId${dokumentreferanse?.let { "/$it" } ?: ""}")
            )
            WireMock.verify(antallGanger, verify)
        }

        fun hentDistribusjonInfoKalt(antallGanger: Int) {
            val verify = WireMock.getRequestedFor(
                WireMock.urlMatching("/dokument/journal/distribuer/info/(.*)")
            )
            WireMock.verify(antallGanger, verify)
        }

        fun bestillDistribusjonKaltMed(journalpostId: String, vararg contains: String, batchId: String? = null) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/dokument/journal/distribuer/$journalpostId(.*)")
            )
            batchId?.let { verify.withQueryParam("batchId", EqualToPattern(batchId)) }
            verifyContains(verify, *contains)
        }

        fun bestillDistribusjonIkkeKalt(journalpostId: String) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/dokument/journal/distribuer/$journalpostId")
            )
            WireMock.verify(0, verify)
        }

        fun hentPersonKaltMed(fnr: String?) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/person/informasjon")
            ).withRequestBody(ContainsPattern(fnr))
            WireMock.verify(verify)
        }

        fun hentPersonSpråkKaltMed(fnr: String?) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/person/spraak")
            ).withRequestBody(ContainsPattern(fnr))
            WireMock.verify(verify)
        }

        fun hentPersonSpråkIkkeKaltMed(fnr: String?) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/person/spraak")
            ).withRequestBody(ContainsPattern(fnr))
            WireMock.verify(0, verify)
        }

        fun bestillDokumentKaltMed(dokumentmal: String, vararg contains: String) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/bestilling/bestill/$dokumentmal")
            )
            verifyContains(verify, *contains)
        }

        fun bestillDokumentIkkeKalt(dokumentmal: String) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/bestilling/bestill/$dokumentmal")
            )
            WireMock.verify(0, verify)
        }

        fun opprettJournalpostIkkeKalt() {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/dokument/journalpost/JOARK")
            )
            WireMock.verify(0, verify)
        }

        fun opprettJournalpostKaltMed(vararg contains: String) {
            val verify = WireMock.postRequestedFor(
                WireMock.urlMatching("/dokument/journalpost/JOARK")
            )
            verifyContains(verify, *contains)
        }

        private fun verifyContains(verify: RequestPatternBuilder, vararg contains: String) {
            Arrays.stream(contains).forEach { verify.withRequestBody(ContainsPattern(it)) }
            WireMock.verify(verify)
        }
    }
}
