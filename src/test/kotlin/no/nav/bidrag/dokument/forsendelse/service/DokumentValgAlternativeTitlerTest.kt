package no.nav.bidrag.dokument.forsendelse.service

import StubUtils
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class DokumentValgAlternativeTitlerTest {

    @MockkBean
    lateinit var bidragDokumentBestillingConsumer: BidragDokumentBestillingConsumer

    @MockkBean
    lateinit var bidragVedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var bidragBehandlingConsumer: BidragBehandlingConsumer

    @MockkBean
    lateinit var tittelService: ForsendelseTittelService

    var dokumentValgService: DokumentValgService? = null

    @BeforeEach
    fun init() {
        dokumentValgService =
            DokumentValgService(bidragDokumentBestillingConsumer, bidragVedtakConsumer, bidragBehandlingConsumer, tittelService, true)
        every { bidragDokumentBestillingConsumer.dokumentmalDetaljer() } returns StubUtils.getDokumentMalDetaljerResponse()
        every { bidragVedtakConsumer.hentVedtak(any()) } returns opprettVedtakDto()
        every { bidragBehandlingConsumer.hentBehandling(any()) } returns opprettBehandlingDto()
        every { tittelService.hentTittelMedPrefiks(any<String>(), any()) } answers {
            val tittel = firstArg<String>()
            tittel
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for innkreving særtilskudd`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.INNKREVING,
                behandlingType = Engangsbeløptype.SAERTILSKUDD.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 2
            fritekstBrev.alternativeTitler shouldContain "Varsel innkreving bidrag til særlige utgifter"
            fritekstBrev.alternativeTitler shouldContain "Orientering innkreving bidrag til særlige utgifter"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for innkreving bidrag`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.INNKREVING,
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 1
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for varsel av eget tiltak bidrag`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.INNKREVING,
                soknadType = "EGET_TILTAK",
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 7
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Varsel om offentlig tilbakekreving"
            fritekstBrev.alternativeTitler shouldContain "Orientering om offentlig tilbakekreving"
            fritekstBrev.alternativeTitler shouldContain "Varsel om endring av barnebidrag barnetillegg"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for vedtak av eget tiltak bidrag`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.ENDRING,
                soknadType = "EGET_TILTAK",
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
                erFattetBeregnet = false
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 1
            fritekstBrev.alternativeTitler shouldContain "Vedtak om offentlig tilbakekreving"
        }
    }
}
