package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BM
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utils.opprettStonadsEndringDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.dokument.forsendelse.HentDokumentValgRequest
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class DokumentValgControllerTest : KontrollerTestRunner() {
    @Test
    fun `Skal hente dokumentvalg for forsendelse`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2().copy(
                    behandlingInfo =
                        BehandlingInfo(
                            erFattetBeregnet = true,
                            stonadType = Stønadstype.BIDRAG,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            vedtakType = Vedtakstype.FASTSETTELSE,
                        ),
                ),
            )

        val dokumentValgResponse = utførHentForsendelseDokumentvalg(forsendelse.forsendelseIdMedPrefix)

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 6
            dokumentValgMap shouldContainKey "BI01B01"
            dokumentValgMap shouldContainKey "BI01B05"
            dokumentValgMap shouldContainKey "BI01B20"
            dokumentValgMap shouldContainKey "BI01B21"
            dokumentValgMap shouldContainKey "BI01S02"
            dokumentValgMap shouldContainKey "BI01S10"

            dokumentValgMap["BI01B01"]!!.beskrivelse shouldBe "Vedtak barnebidrag"
            dokumentValgMap["BI01B01"]!!.type shouldBe DokumentMalType.UTGÅENDE

            dokumentValgMap["BI01B05"]!!.beskrivelse shouldBe "Vedtak automatisk justering av barnebidrag"
            dokumentValgMap["BI01B20"]!!.beskrivelse shouldBe "Vedtak utland skjønn fastsettelse"
            dokumentValgMap["BI01B21"]!!.beskrivelse shouldBe "Vedtak utland skjønn endring"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "Oversendelse av informasjon"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for forsendelse for vedtak ikke tilbakekreving`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfo(
                            engangsBelopType = Engangsbeløptype.TILBAKEKREVING,
                            vedtakType = Vedtakstype.KLAGE,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            erFattetBeregnet = false,
                            erVedtakIkkeTilbakekreving = true,
                        ),
                ),
            )
        val dokumentValgResponse = utførHentForsendelseDokumentvalg(forsendelse.forsendelseIdMedPrefix)

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 3
            dokumentValgMap shouldContainKey "BI01K50"
            dokumentValgMap shouldContainKey "BI01S02"
            dokumentValgMap shouldContainKey "BI01S10"

            dokumentValgMap["BI01K50"]!!.beskrivelse shouldBe "Klage - vedtak tilbakekreving"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "Oversendelse av informasjon"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for forsendelse med vedtakId`() {
        val vedtakId = "123213213"
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfo(
                            vedtakId = vedtakId,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                        ),
                ),
            )
        stubUtils.stubVedtak(
            opprettVedtakDto().copy(
                type = Vedtakstype.FASTSETTELSE,
                stønadsendringListe = listOf(opprettStonadsEndringDto().copy(type = Stønadstype.FORSKUDD)),
            ),
        )
        val dokumentValgResponse = utførHentForsendelseDokumentvalg(forsendelse.forsendelseIdMedPrefix)

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 3
            dokumentValgMap shouldContainKey "BI01A01"
            dokumentValgMap shouldContainKey "BI01S02"
            dokumentValgMap shouldContainKey "BI01S10"

            dokumentValgMap["BI01A01"]!!.beskrivelse shouldBe "Vedtak bidragsforskudd"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "Oversendelse av informasjon"
            stubUtils.Valider().hentVedtakKalt(vedtakId)
        }
    }

    @Test
    fun `Skal hente dokumentvalg for forsendelse med behandlingId`() {
        val behandlingId = "123213213"
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfo(
                            behandlingId = behandlingId,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                        ),
                ),
            )
        stubUtils.stubBehandling(
            opprettBehandlingDto().copy(
                vedtakstype = Vedtakstype.REVURDERING,
                stønadstype = Stønadstype.FORSKUDD,
                søktAv = SøktAvType.NAV_BIDRAG,
            ),
        )
        val dokumentValgResponse = utførHentForsendelseDokumentvalg(forsendelse.forsendelseIdMedPrefix)

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 4
            dokumentValgMap shouldContainKey "BI01S08"
            dokumentValgMap shouldContainKey "BI01S27"
            dokumentValgMap shouldContainKey "BI01S02"
            dokumentValgMap shouldContainKey "BI01S10"

            dokumentValgMap["BI01S08"]!!.beskrivelse shouldBe "Varsel revurd forskudd"
            dokumentValgMap["BI01S27"]!!.beskrivelse shouldBe "Varsel om ny beregning av bidragsforskudd og varsel om mulig tilbakebetaling"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "Oversendelse av informasjon"
            stubUtils.Valider().hentBehandlingKalt(behandlingId)
        }
    }

    @Test
    fun `Skal hente dokumentvalg for standardbrev`() {
        val dokumentValgResponse = utførHentDokumentvalg()

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 2
            dokumentValgMap shouldContainKey "BI01S02"
            dokumentValgMap shouldContainKey "BI01S10"

            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "Oversendelse av informasjon"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtakId`() {
        val vedtakId = "123213213"
        stubUtils.stubVedtak(
            opprettVedtakDto().copy(
                type = Vedtakstype.FASTSETTELSE,
                stønadsendringListe = listOf(opprettStonadsEndringDto().copy(type = Stønadstype.FORSKUDD)),
            ),
        )
        val dokumentValgResponse =
            utførHentDokumentvalg(HentDokumentValgRequest(vedtakId = vedtakId, soknadFra = SøktAvType.BIDRAGSMOTTAKER))

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 3
            dokumentValgMap shouldContainKey "BI01A01"
            dokumentValgMap shouldContainKey "BI01S02"
            dokumentValgMap shouldContainKey "BI01S10"

            dokumentValgMap["BI01A01"]!!.beskrivelse shouldBe "Vedtak bidragsforskudd"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "Oversendelse av informasjon"
            stubUtils.Valider().hentVedtakKalt(vedtakId)
        }
    }

    @Test
    fun `Skal hente dokumentvalg for parametere`() {
        stubUtils.stubVedtak(
            opprettVedtakDto().copy(
                type = Vedtakstype.FASTSETTELSE,
                stønadsendringListe = listOf(opprettStonadsEndringDto().copy(type = Stønadstype.FORSKUDD)),
            ),
        )
        val dokumentValgResponse =
            utførHentDokumentvalg(
                HentDokumentValgRequest(
                    vedtakType = Vedtakstype.FASTSETTELSE,
                    soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                    behandlingType = Stønadstype.FORSKUDD.name,
                    erFattetBeregnet = true,
                ),
            )

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 3
            dokumentValgMap shouldContainKey "BI01A01"
            dokumentValgMap shouldContainKey "BI01S02"
            dokumentValgMap shouldContainKey "BI01S10"

            dokumentValgMap["BI01A01"]!!.beskrivelse shouldBe "Vedtak bidragsforskudd"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "Oversendelse av informasjon"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for notater`() {
        val dokumentValgResponse = utførHentForsendelseDokumentvalgNotat()

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 4
            dokumentValgMap shouldContainKey "BI01P11"
            dokumentValgMap shouldContainKey "BI01P18"
            dokumentValgMap shouldContainKey "BI01X01"
            dokumentValgMap shouldContainKey "BI01X02"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for notater for klage`() {
        val dokumentValgResponse = utførHentForsendelseDokumentvalgNotat(HentDokumentValgRequest(vedtakType = Vedtakstype.KLAGE))

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 5
            dokumentValgMap shouldContainKey "BI01P17"
            dokumentValgMap shouldContainKey "BI01P11"
            dokumentValgMap shouldContainKey "BI01P18"
            dokumentValgMap shouldContainKey "BI01X01"
            dokumentValgMap shouldContainKey "BI01X02"
        }
    }

    fun utførHentForsendelseDokumentvalg(forsendelseId: String): ResponseEntity<Map<String, DokumentMalDetaljer>> =
        httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/dokumentvalg/forsendelse/$forsendelseId",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<Map<String, DokumentMalDetaljer>>() {},
        )

    fun utførHentForsendelseDokumentvalgNotat(request: HentDokumentValgRequest? = null): ResponseEntity<Map<String, DokumentMalDetaljer>> =
        httpHeaderTestRestTemplate.postForEntity(
            "${rootUri()}/dokumentvalg/notat",
            request?.let { HttpEntity(request) },
        )

    fun utførHentDokumentvalg(request: HentDokumentValgRequest? = null): ResponseEntity<Map<String, DokumentMalDetaljer>> =
        httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/dokumentvalg",
            HttpMethod.POST,
            request?.let { HttpEntity(request) },
            object : ParameterizedTypeReference<Map<String, DokumentMalDetaljer>>() {},
        )
}
