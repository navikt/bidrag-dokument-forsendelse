package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.felles.enums.EngangsbelopType
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BM
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utils.opprettStonadsEndringDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class DokumentValgControllerTest : KontrollerTestRunner() {

    @Test
    fun `Skal hente dokumentvalg for forsendelse`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2().copy(
                behandlingInfo = BehandlingInfo(
                    erFattetBeregnet = true,
                    stonadType = StonadType.BIDRAG,
                    soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                    vedtakType = VedtakType.FASTSETTELSE
                )
            )
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

            dokumentValgMap["BI01B05"]!!.beskrivelse shouldBe "VEDTAK AUTOMATISK JUSTERING BARNEBIDRAG"
            dokumentValgMap["BI01B20"]!!.beskrivelse shouldBe "Vedtak utland skjønn fastsettelse"
            dokumentValgMap["BI01B21"]!!.beskrivelse shouldBe "Vedtak utland skjønn endring"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "KOPIFORSIDE T"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for forsendelse for vedtak ikke tilbakekreving`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                gjelderIdent = GJELDER_IDENT_BM,
                behandlingInfo = BehandlingInfo(
                    engangsBelopType = EngangsbelopType.TILBAKEKREVING,
                    vedtakType = VedtakType.KLAGE,
                    soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                    erFattetBeregnet = false,
                    erVedtakIkkeTilbakekreving = true
                )
            )
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
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "KOPIFORSIDE T"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for forsendelse med vedtakId`() {
        val vedtakId = "123213213"
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                gjelderIdent = GJELDER_IDENT_BM,
                behandlingInfo = BehandlingInfo(
                    vedtakId = vedtakId,
                    soknadFra = SoknadFra.BIDRAGSMOTTAKER
                )
            )
        )
        stubUtils.stubVedtak(
            opprettVedtakDto().copy(
                type = VedtakType.FASTSETTELSE,
                stonadsendringListe = listOf(opprettStonadsEndringDto().copy(type = StonadType.FORSKUDD))

            )
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
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "KOPIFORSIDE T"
            stubUtils.Valider().hentVedtakKalt(vedtakId)
        }
    }

    @Test
    fun `Skal hente dokumentvalg for forsendelse med behandlingId`() {
        val behandlingId = "123213213"
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                gjelderIdent = GJELDER_IDENT_BM,
                behandlingInfo = BehandlingInfo(
                    behandlingId = behandlingId,
                    soknadFra = SoknadFra.BIDRAGSMOTTAKER
                )
            )
        )
        stubUtils.stubBehandling(
            opprettBehandlingDto().copy(
                soknadType = VedtakType.REVURDERING,
                behandlingType = StonadType.FORSKUDD.name,
                soknadFraType = SoknadFra.NAV_BIDRAG

            )
        )
        val dokumentValgResponse = utførHentForsendelseDokumentvalg(forsendelse.forsendelseIdMedPrefix)

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 3
            dokumentValgMap shouldContainKey "BI01S08"
            dokumentValgMap shouldContainKey "BI01S27"
            dokumentValgMap shouldContainKey "BI01S02"

            dokumentValgMap["BI01S08"]!!.beskrivelse shouldBe "Varsel revurd forskudd"
            dokumentValgMap["BI01S27"]!!.beskrivelse shouldBe "Varsel opphør av bidragsforskudd tilbake i tid"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
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
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "KOPIFORSIDE T"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtakId`() {
        val vedtakId = "123213213"
        stubUtils.stubVedtak(
            opprettVedtakDto().copy(
                type = VedtakType.FASTSETTELSE,
                stonadsendringListe = listOf(opprettStonadsEndringDto().copy(type = StonadType.FORSKUDD))

            )
        )
        val dokumentValgResponse = utførHentDokumentvalg(HentDokumentValgRequest(vedtakId = vedtakId, soknadFra = SoknadFra.BIDRAGSMOTTAKER))

        assertSoftly {
            dokumentValgResponse.statusCode shouldBe HttpStatus.OK
            val dokumentValgMap = dokumentValgResponse.body!!
            dokumentValgMap.size shouldBe 3
            dokumentValgMap shouldContainKey "BI01A01"
            dokumentValgMap shouldContainKey "BI01S02"
            dokumentValgMap shouldContainKey "BI01S10"

            dokumentValgMap["BI01A01"]!!.beskrivelse shouldBe "Vedtak bidragsforskudd"
            dokumentValgMap["BI01S02"]!!.beskrivelse shouldBe "Fritekstbrev"
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "KOPIFORSIDE T"
            stubUtils.Valider().hentVedtakKalt(vedtakId)
        }
    }

    @Test
    fun `Skal hente dokumentvalg for parametere`() {
        val vedtakId = "123213213"
        stubUtils.stubVedtak(
            opprettVedtakDto().copy(
                type = VedtakType.FASTSETTELSE,
                stonadsendringListe = listOf(opprettStonadsEndringDto().copy(type = StonadType.FORSKUDD))

            )
        )
        val dokumentValgResponse = utførHentDokumentvalg(
            HentDokumentValgRequest(
                vedtakType = VedtakType.FASTSETTELSE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.FORSKUDD.name,
                erFattetBeregnet = true
            )
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
            dokumentValgMap["BI01S10"]!!.beskrivelse shouldBe "KOPIFORSIDE T"
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

    fun utførHentForsendelseDokumentvalg(
        forsendelseId: String
    ): ResponseEntity<Map<String, DokumentMalDetaljer>> {
        return httpHeaderTestRestTemplate.getForEntity(
            "${rootUri()}/dokumentvalg/forsendelse/$forsendelseId"
        )
    }

    fun utførHentForsendelseDokumentvalgNotat(): ResponseEntity<Map<String, DokumentMalDetaljer>> {
        return httpHeaderTestRestTemplate.getForEntity(
            "${rootUri()}/dokumentvalg/notat"
        )
    }

    fun utførHentDokumentvalg(
        request: HentDokumentValgRequest? = null
    ): ResponseEntity<Map<String, DokumentMalDetaljer>> {
        return httpHeaderTestRestTemplate.postForEntity(
            "${rootUri()}/dokumentvalg",
            request?.let { HttpEntity(request) }
        )
    }
}
