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
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType
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

    var dokumentValgService: DokumentValgService? = null

    @BeforeEach
    fun init() {
        dokumentValgService = DokumentValgService(bidragDokumentBestillingConsumer, bidragVedtakConsumer, bidragBehandlingConsumer)
        every { bidragDokumentBestillingConsumer.dokumentmalDetaljer() } returns StubUtils.getDokumentMalDetaljerResponse()
        every { bidragVedtakConsumer.hentVedtak(any()) } returns opprettVedtakDto()
        every { bidragBehandlingConsumer.hentBehandling(any()) } returns opprettBehandlingDto()
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for innkreving særtilskudd`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.INNKREVING,
                behandlingType = EngangsbelopType.SAERTILSKUDD.name,
                soknadFra = SoknadFra.NAV_BIDRAG,
                erFattetBeregnet = null,
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
                vedtakType = VedtakType.INNKREVING,
                behandlingType = StonadType.BIDRAG.name,
                soknadFra = SoknadFra.NAV_BIDRAG,
                erFattetBeregnet = null,
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
}
