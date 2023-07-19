package no.nav.bidrag.dokument.forsendelse.service

import StubUtils
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.bidrag.behandling.felles.enums.EngangsbelopType
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.model.KLAGE_ANKE_ENHET
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class DokumentValgServiceTest {

    @MockkBean
    lateinit var bidragDokumentBestillingConsumer: BidragDokumentBestillingConsumer

    @MockkBean
    lateinit var bidragVedtakConsumer: BidragVedtakConsumer


    var dokumentValgService: DokumentValgService? = null

    @BeforeEach
    fun init() {
        dokumentValgService = DokumentValgService(bidragDokumentBestillingConsumer, bidragVedtakConsumer)
        every { bidragDokumentBestillingConsumer.dokumentmalDetaljer() } returns StubUtils.getDokumentMalDetaljerResponse()
    }

    @Test
    fun `Skal hente standardbrev`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe()

        assertSoftly {
            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for bidrag søknad som er fastsatt manuelt`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.FASTSETTELSE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = false
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01G01"
            dokumentValgListe["BI01G01"]!!.beskrivelse shouldBe "Vedtak innkrev. barnebidrag og gjeld"
            dokumentValgListe shouldContainKey "BI01G02"
            dokumentValgListe["BI01G02"]!!.beskrivelse shouldBe "Vedtak innkreving opphør"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for bidrag søknad som er fastsatt beregnet`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.FASTSETTELSE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = true
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 6
            dokumentValgListe shouldContainKey "BI01B01"
            dokumentValgListe shouldContainKey "BI01B05"
            dokumentValgListe shouldContainKey "BI01B20"
            dokumentValgListe shouldContainKey "BI01B21"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for klageenhet av bidrag søknad for endring `() {
        val dokumentValgListeKlageEnhet = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.ENDRING,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = false,
                enhet = KLAGE_ANKE_ENHET.ENHET_KLANKE_OSLO_AKERSHUS.kode
            )
        )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.ENDRING,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = false
            )
        )

        assertSoftly {
            dokumentValgListeKlageEnhet.size shouldBe 3
            dokumentValgListeKlageEnhet shouldContainKey "BI01G50"
            dokumentValgListeKlageEnhet shouldContainKey "BI01S02"
            dokumentValgListeKlageEnhet shouldContainKey "BI01S10"

            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01G01"
            dokumentValgListe shouldContainKey "BI01G02"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for klage av bidrag søknad `() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.KLAGE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = true
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01B50"
            dokumentValgListe shouldContainKey "BI01G50"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for manuelt beregnet bidrag søknad`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.ENDRING,
                soknadFra = SoknadFra.NAV_BIDRAG,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = false
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01S07"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for manuelt beregnet bidrag søknad behandlet av klageenhet`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.ENDRING,
                soknadFra = SoknadFra.NAV_BIDRAG,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = true,
                enhet = KLAGE_ANKE_ENHET.ENHET_KLANKE_OSLO_AKERSHUS.kode
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01B50"
            dokumentValgListe shouldContainKey "BI01G50"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for manuelt beregnet bidrag søknad type opphør`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.OPPHØR,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = false,
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01G01"
            dokumentValgListe shouldContainKey "BI01G02"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak ikke tilbakekreving`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.ENDRING,
                soknadFra = SoknadFra.NAV_BIDRAG,
                behandlingType = EngangsbelopType.TILBAKEKREVING.name,
                erFattetBeregnet = false,
                erVedtakIkkeTilbakekreving = true
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01A05"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for klage til vedtak ikke tilbakekreving`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.KLAGE,
                soknadFra = SoknadFra.NAV_BIDRAG,
                behandlingType = StonadType.FORSKUDD.name,
                erFattetBeregnet = false,
                erVedtakIkkeTilbakekreving = true
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01K50"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente notater`() {
        val dokumentValgListe = dokumentValgService!!.hentNotatListe()

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01P11"
            dokumentValgListe shouldContainKey "BI01P18"
            dokumentValgListe shouldContainKey "BI01X01"
            dokumentValgListe shouldContainKey "BI01X02"
        }
    }
}