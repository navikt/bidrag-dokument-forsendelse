package no.nav.bidrag.dokument.forsendelse.service

import StubUtils
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.model.KLAGE_ANKE_ENHET
import no.nav.bidrag.dokument.forsendelse.model.ResultatKode
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettEngangsbelopDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettStonadsEndringDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType
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
    fun `Skal hente dokumentvalg for varsel bidrag søknad`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.FASTSETTELSE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01S01"
            dokumentValgListe shouldContainKey "BI01S12"
            dokumentValgListe shouldContainKey "BI01S52"
//            dokumentValgListe shouldContainKey "BI01S53"
            dokumentValgListe shouldContainKey "BI01S02"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for motregning vedtak`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.ENDRING,
                soknadFra = SoknadFra.BIDRAGSPLIKTIG,
                behandlingType = StonadType.MOTREGNING.name,
                erFattetBeregnet = false
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 2
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
    fun `Skal hente dokumentvalg for varsling bidrag klage`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.KLAGE,
                soknadFra = SoknadFra.BIDRAGSPLIKTIG,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 7
            dokumentValgListe shouldContainKey "BI01P17"
            dokumentValgListe shouldContainKey "BI01S20"
            dokumentValgListe shouldContainKey "BI01S21"
            dokumentValgListe shouldContainKey "BI01S60"
            dokumentValgListe shouldContainKey "BI01S64"
            dokumentValgListe shouldContainKey "BI01S65"
            dokumentValgListe shouldContainKey "BI01S02"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for varsling revurdering bidrag`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.REVURDERING,
                soknadFra = SoknadFra.NAV_BIDRAG,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 13
            dokumentValgListe shouldContainKey "BI01S06"
            dokumentValgListe shouldContainKey "BI01S07"
            dokumentValgListe shouldContainKey "BI01S31"
            dokumentValgListe shouldContainKey "BI01S32"
            dokumentValgListe shouldContainKey "BI01S34"
            dokumentValgListe shouldContainKey "BI01S35"
            dokumentValgListe shouldContainKey "BI01S36"
            dokumentValgListe shouldContainKey "BI01S46"
            dokumentValgListe shouldContainKey "BI01S62"
            dokumentValgListe shouldContainKey "BI01S63"
            dokumentValgListe shouldContainKey "BI01S65"
            dokumentValgListe shouldContainKey "BI01S02"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak revurdering bidrag`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.REVURDERING,
                soknadFra = SoknadFra.NAV_BIDRAG,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = true
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 7
            dokumentValgListe shouldContainKey "BI01B01"
            dokumentValgListe shouldContainKey "BI01B04"
            dokumentValgListe shouldContainKey "BI01B05"
            dokumentValgListe shouldContainKey "BI01B20"
            dokumentValgListe shouldContainKey "BI01B21"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak bidrag klage`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.KLAGE,
                soknadFra = SoknadFra.BIDRAGSPLIKTIG,
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
    fun `Skal hente dokumentvalg for manuelt beregnet bidrag søknad type opphør`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.OPPHØR,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.BIDRAG.name,
                erFattetBeregnet = false
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
    fun `Skal hente dokumentvalg for vedtak opphør 18 åring`() {
        val vedtakId = "21321321"
        every { bidragVedtakConsumer.hentVedtak(eq(vedtakId)) } returns opprettVedtakDto()
            .copy(
                type = VedtakType.OPPHØR,
                stonadsendringListe = listOf(opprettStonadsEndringDto().copy(type = StonadType.BIDRAG18AAR))
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadFra = SoknadFra.NAV_BIDRAG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01B10"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
            verify { bidragVedtakConsumer.hentVedtak(vedtakId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak særtilskudd`() {
        val vedtakId = "21321321"
        every { bidragVedtakConsumer.hentVedtak(eq(vedtakId)) } returns opprettVedtakDto()
            .copy(
                type = VedtakType.ENDRING,
                stonadsendringListe = emptyList(),
                engangsbelopListe = listOf(
                    opprettEngangsbelopDto(EngangsbelopType.SAERTILSKUDD)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01E01"
            dokumentValgListe shouldContainKey "BI01E02"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
            verify { bidragVedtakConsumer.hentVedtak(vedtakId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak særtilskudd innkreving`() {
        val vedtakId = "21321321"
        every { bidragVedtakConsumer.hentVedtak(eq(vedtakId)) } returns opprettVedtakDto()
            .copy(
                type = VedtakType.INNKREVING,
                stonadsendringListe = emptyList(),
                grunnlagListe = emptyList(),
                engangsbelopListe = listOf(
                    opprettEngangsbelopDto(EngangsbelopType.SAERTILSKUDD)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01G04"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
            verify { bidragVedtakConsumer.hentVedtak(vedtakId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak ikke tilbakekreving fra vedtakId`() {
        val vedtakId = "21321321"
        every { bidragVedtakConsumer.hentVedtak(eq(vedtakId)) } returns opprettVedtakDto()
            .copy(
                type = VedtakType.ENDRING,
                stonadsendringListe = emptyList(),
                grunnlagListe = emptyList(),
                engangsbelopListe = listOf(
                    opprettEngangsbelopDto(EngangsbelopType.TILBAKEKREVING, ResultatKode.IKKE_TILBAKEKREVING)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadFra = SoknadFra.NAV_BIDRAG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01A05"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
            verify { bidragVedtakConsumer.hentVedtak(vedtakId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak klage ikke tilbakekreving fra vedtakId`() {
        val vedtakId = "21321321"
        every { bidragVedtakConsumer.hentVedtak(eq(vedtakId)) } returns opprettVedtakDto()
            .copy(
                type = VedtakType.KLAGE,
                stonadsendringListe = emptyList(),
                grunnlagListe = emptyList(),
                engangsbelopListe = listOf(
                    opprettEngangsbelopDto(EngangsbelopType.TILBAKEKREVING, ResultatKode.IKKE_TILBAKEKREVING)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                behandlingId = "123213",
                soknadFra = SoknadFra.BIDRAGSMOTTAKER
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01K50"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
            verify { bidragVedtakConsumer.hentVedtak(vedtakId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg for behandlingId`() {
        val behandlingId = "21321321"
        every { bidragBehandlingConsumer.hentBehandling(eq(behandlingId)) } returns opprettBehandlingDto()
            .copy(
                soknadType = VedtakType.ENDRING,
                behandlingType = StonadType.BIDRAG.name,
                soknadFraType = SoknadFra.BIDRAGSMOTTAKER
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                behandlingId = behandlingId,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01S14"
            dokumentValgListe shouldContainKey "BI01S26"
            dokumentValgListe shouldContainKey "BI01S47"
//            dokumentValgListe shouldContainKey "BI01S48"
//            dokumentValgListe shouldContainKey "BI01S49"
            dokumentValgListe shouldContainKey "BI01S02"
            verify { bidragBehandlingConsumer.hentBehandling(behandlingId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg fra paramtere hvis behandlingId finnes men er fattet`() {
        val behandlingId = "21321321"
        every { bidragBehandlingConsumer.hentBehandling(eq(behandlingId)) } returns opprettBehandlingDto()
            .copy(
                soknadType = VedtakType.ENDRING,
                behandlingType = StonadType.BIDRAG.name,
                soknadFraType = SoknadFra.BIDRAGSMOTTAKER
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                behandlingId = behandlingId,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = StonadType.FORSKUDD.name,
                vedtakType = VedtakType.FASTSETTELSE,
                erFattetBeregnet = true
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01A01"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
            verify(exactly = 0) { bidragBehandlingConsumer.hentBehandling(behandlingId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg for varsel farskap`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.FASTSETTELSE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = "FARSKAP",
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 6
            dokumentValgListe shouldContainKey "BI01H01"
            dokumentValgListe shouldContainKey "BI01H02"
            dokumentValgListe shouldContainKey "BI01H03"
            dokumentValgListe shouldContainKey "BI01H04"
            dokumentValgListe shouldContainKey "BI01H05"
            dokumentValgListe shouldContainKey "BI01S02"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak farskap`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.FASTSETTELSE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = "FARSKAP",
                erFattetBeregnet = false
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 7
            dokumentValgListe shouldContainKey "BI01H01"
            dokumentValgListe shouldContainKey "BI01H02"
            dokumentValgListe shouldContainKey "BI01H03"
            dokumentValgListe shouldContainKey "BI01H04"
            dokumentValgListe shouldContainKey "BI01H05"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for klage farskap`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.KLAGE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = "FARSKAP",
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
    fun `Skal hente dokumentvalg varsel for klage farskap fra Bidragspliktig`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.KLAGE,
                soknadFra = SoknadFra.BIDRAGSPLIKTIG,
                behandlingType = "FARSKAP",
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01P17"
            dokumentValgListe shouldContainKey "BI01S60"
            dokumentValgListe shouldContainKey "BI01S64"
            dokumentValgListe shouldContainKey "BI01S02"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for klage farskap fra klage enhet`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.KLAGE,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                behandlingType = "FARSKAP",
                enhet = KLAGE_ANKE_ENHET.ENHET_KLANKE_OSLO_AKERSHUS.kode,
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
    fun `Skal hente dokumentvalg for vedtak særtilskudd og ignorere behandlingtype`() {
        val vedtakId = "21321321"
        every { bidragVedtakConsumer.hentVedtak(eq(vedtakId)) } returns opprettVedtakDto()
            .copy(
                type = VedtakType.ENDRING,
                stonadsendringListe = emptyList(),
                engangsbelopListe = listOf(
                    opprettEngangsbelopDto(EngangsbelopType.SAERTILSKUDD)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadType = "FOLGER_KLAGE",
                soknadFra = SoknadFra.BIDRAGSMOTTAKER
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01E01"
            dokumentValgListe shouldContainKey "BI01E02"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
            verify { bidragVedtakConsumer.hentVedtak(vedtakId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg for tilbakekreving varsel med soknad type EGET_TILTAK`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.ENDRING,
                soknadType = "EGET_TILTAK",
                behandlingType = EngangsbelopType.TILBAKEKREVING.name,
                soknadFra = SoknadFra.NAV_BIDRAG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01S54"
//            dokumentValgListe shouldContainKey "BI01S55"
//            dokumentValgListe shouldContainKey "BI01S56"
//            dokumentValgListe shouldContainKey "BI01S57"
//            dokumentValgListe shouldContainKey "BI01S58"
            dokumentValgListe shouldContainKey "BI01S59"
            dokumentValgListe shouldContainKey "BI01S02"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for bidrag varsel med soknad type EGET_TILTAK fra NAV_BIDRAG`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.ENDRING,
                soknadType = "EGET_TILTAK",
                behandlingType = StonadType.BIDRAG.name,
                soknadFra = SoknadFra.NAV_BIDRAG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 13
            dokumentValgListe shouldContainKey "BI01S06"
            dokumentValgListe shouldContainKey "BI01S07"
            dokumentValgListe shouldContainKey "BI01S31"
            dokumentValgListe shouldContainKey "BI01S32"
            dokumentValgListe shouldContainKey "BI01S33"
            dokumentValgListe shouldContainKey "BI01S34"
            dokumentValgListe shouldContainKey "BI01S35"
            dokumentValgListe shouldContainKey "BI01S36"
            dokumentValgListe shouldContainKey "BI01S46"
            dokumentValgListe shouldContainKey "BI01S62"
            dokumentValgListe shouldContainKey "BI01S63"
            dokumentValgListe shouldContainKey "BI01S65"
            dokumentValgListe shouldContainKey "BI01S02"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for revurdering`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = VedtakType.REVURDERING,
                soknadType = "BEGRENSET_REVURDERING",
                behandlingType = StonadType.BIDRAG.name,
                soknadFra = SoknadFra.NAV_BIDRAG,
                erFattetBeregnet = true
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 7
            dokumentValgListe shouldContainKey "BI01B01"
            dokumentValgListe shouldContainKey "BI01B04"
            dokumentValgListe shouldContainKey "BI01B05"
            dokumentValgListe shouldContainKey "BI01B20"
            dokumentValgListe shouldContainKey "BI01B21"
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
