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
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettEngangsbelopDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettStonadsEndringDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.domene.enums.Engangsbeløptype
import no.nav.bidrag.domene.enums.Stønadstype
import no.nav.bidrag.domene.enums.SøktAvType
import no.nav.bidrag.domene.enums.Vedtakstype
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

    @MockkBean
    lateinit var sakService: SakService
    var tittelService: ForsendelseTittelService? = null
    var dokumentValgService: DokumentValgService? = null

    @BeforeEach
    fun init() {
        tittelService = ForsendelseTittelService(sakService, bidragVedtakConsumer, bidragBehandlingConsumer)
        dokumentValgService =
            DokumentValgService(bidragDokumentBestillingConsumer, bidragVedtakConsumer, bidragBehandlingConsumer, tittelService!!, true)
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
                vedtakType = Vedtakstype.FASTSETTELSE,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.FASTSETTELSE,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Stønadstype.BIDRAG.name,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 5
            dokumentValgListe shouldContainKey "BI01S01"
            dokumentValgListe shouldContainKey "BI01S12"
            dokumentValgListe shouldContainKey "BI01S52"
//            dokumentValgListe shouldContainKey "BI01S53"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for motregning vedtak`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.ENDRING,
                soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                behandlingType = Stønadstype.MOTREGNING.name,
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
                vedtakType = Vedtakstype.FASTSETTELSE,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.ENDRING,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Stønadstype.BIDRAG.name,
                erFattetBeregnet = false,
                enhet = KLAGE_ANKE_ENHET.ENHET_KLANKE_OSLO_AKERSHUS.kode
            )
        )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.ENDRING,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.KLAGE,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.ENDRING,
                soknadFra = SøktAvType.NAV_BIDRAG,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.ENDRING,
                soknadFra = SøktAvType.NAV_BIDRAG,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.KLAGE,
                soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                behandlingType = Stønadstype.BIDRAG.name,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 6
            dokumentValgListe shouldContainKey "BI01S20"
            dokumentValgListe shouldContainKey "BI01S21"
            dokumentValgListe shouldContainKey "BI01S60"
            dokumentValgListe shouldContainKey "BI01S64"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for varsling revurdering bidrag`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.REVURDERING,
                soknadFra = SøktAvType.NAV_BIDRAG,
                behandlingType = Stønadstype.BIDRAG.name,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 7
            dokumentValgListe shouldContainKey "BI01S06"
            dokumentValgListe shouldContainKey "BI01S07"
            dokumentValgListe shouldContainKey "BI01S35"
            dokumentValgListe shouldContainKey "BI01S46"
            dokumentValgListe shouldContainKey "BI01S62"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for varsling begrenset revurdering bidrag`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.REVURDERING,
                soknadFra = SøktAvType.NAV_BIDRAG,
                soknadType = "BEGRENSET_REVURDERING",
                behandlingType = Stønadstype.BIDRAG.name,
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01S07"
            dokumentValgListe shouldContainKey "BI01S22"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak revurdering bidrag`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.REVURDERING,
                soknadFra = SøktAvType.NAV_BIDRAG,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.KLAGE,
                soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.OPPHØR,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Stønadstype.BIDRAG.name,
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
                vedtakType = Vedtakstype.ENDRING,
                soknadFra = SøktAvType.NAV_BIDRAG,
                behandlingType = Engangsbeløptype.TILBAKEKREVING.name,
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
                vedtakType = Vedtakstype.KLAGE,
                soknadFra = SøktAvType.NAV_BIDRAG,
                behandlingType = Stønadstype.FORSKUDD.name,
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
                type = Vedtakstype.OPPHØR,
                stønadsendringListe = listOf(opprettStonadsEndringDto().copy(type = Stønadstype.BIDRAG18AAR))
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadFra = SøktAvType.NAV_BIDRAG
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
    fun `Skal hente dokumentvalg for vedtak særtilskudd fra vedtakId`() {
        val vedtakId = "21321321"
        every { bidragVedtakConsumer.hentVedtak(eq(vedtakId)) } returns opprettVedtakDto()
            .copy(
                type = Vedtakstype.ENDRING,
                stønadsendringListe = emptyList(),
                engangsbeløpListe = listOf(
                    opprettEngangsbelopDto(Engangsbeløptype.SAERTILSKUDD)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER
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
    fun `Skal hente dokumentvalg for vedtak særtilskudd`() {
        val vedtakId = "21321321"
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.ENDRING,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Engangsbeløptype.SAERTILSKUDD.name,
                erFattetBeregnet = true
            )
        )
        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01E01"
            dokumentValgListe shouldContainKey "BI01E02"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak særtilskudd innkreving`() {
        val vedtakId = "21321321"
        every { bidragVedtakConsumer.hentVedtak(eq(vedtakId)) } returns opprettVedtakDto()
            .copy(
                type = Vedtakstype.INNKREVING,
                stønadsendringListe = emptyList(),
                grunnlagListe = emptyList(),
                engangsbeløpListe = listOf(
                    opprettEngangsbelopDto(Engangsbeløptype.SAERTILSKUDD)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER
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
                type = Vedtakstype.ENDRING,
                stønadsendringListe = emptyList(),
                grunnlagListe = emptyList(),
                engangsbeløpListe = listOf(
                    opprettEngangsbelopDto(Engangsbeløptype.TILBAKEKREVING, ResultatKode.IKKE_TILBAKEKREVING)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadFra = SøktAvType.NAV_BIDRAG
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
                type = Vedtakstype.KLAGE,
                stønadsendringListe = emptyList(),
                grunnlagListe = emptyList(),
                engangsbeløpListe = listOf(
                    opprettEngangsbelopDto(Engangsbeløptype.TILBAKEKREVING, ResultatKode.IKKE_TILBAKEKREVING)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                behandlingId = "123213",
                soknadFra = SøktAvType.BIDRAGSMOTTAKER
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
                soknadType = Vedtakstype.ENDRING,
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFraType = SøktAvType.BIDRAGSMOTTAKER
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                behandlingId = behandlingId,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 5
            dokumentValgListe shouldContainKey "BI01S14"
            dokumentValgListe shouldContainKey "BI01S26"
            dokumentValgListe shouldContainKey "BI01S47"
//            dokumentValgListe shouldContainKey "BI01S48"
//            dokumentValgListe shouldContainKey "BI01S49"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
            verify { bidragBehandlingConsumer.hentBehandling(behandlingId) }
        }
    }

    @Test
    fun `Skal hente dokumentvalg fra paramtere hvis behandlingId finnes men er fattet`() {
        val behandlingId = "21321321"
        every { bidragBehandlingConsumer.hentBehandling(eq(behandlingId)) } returns opprettBehandlingDto()
            .copy(
                soknadType = Vedtakstype.ENDRING,
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFraType = SøktAvType.BIDRAGSMOTTAKER
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                behandlingId = behandlingId,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = Stønadstype.FORSKUDD.name,
                vedtakType = Vedtakstype.FASTSETTELSE,
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
                vedtakType = Vedtakstype.FASTSETTELSE,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                behandlingType = "FARSKAP",
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 7
            dokumentValgListe shouldContainKey "BI01H01"
            dokumentValgListe shouldContainKey "BI01H02"
            dokumentValgListe shouldContainKey "BI01H03"
            dokumentValgListe shouldContainKey "BI01H04"
            dokumentValgListe shouldContainKey "BI01H05"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak farskap`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.FASTSETTELSE,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
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
                vedtakType = Vedtakstype.KLAGE,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
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
                vedtakType = Vedtakstype.KLAGE,
                soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                behandlingType = "FARSKAP",
                erFattetBeregnet = null
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 6
            dokumentValgListe shouldContainKey "BI01S20"
            dokumentValgListe shouldContainKey "BI01S21"
            dokumentValgListe shouldContainKey "BI01S60"
            dokumentValgListe shouldContainKey "BI01S64"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for klage farskap fra klage enhet`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.KLAGE,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
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
                type = Vedtakstype.ENDRING,
                stønadsendringListe = emptyList(),
                engangsbeløpListe = listOf(
                    opprettEngangsbelopDto(Engangsbeløptype.SAERTILSKUDD)
                )
            )

        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakId = vedtakId,
                soknadType = "FOLGER_KLAGE",
                soknadFra = SøktAvType.BIDRAGSMOTTAKER
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
                vedtakType = Vedtakstype.ENDRING,
                soknadType = "EGET_TILTAK",
                behandlingType = Engangsbeløptype.TILBAKEKREVING.name,
                soknadFra = SøktAvType.NAV_BIDRAG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 8
            dokumentValgListe shouldContainKey "BI01S54"
            dokumentValgListe shouldContainKey "BI01S55"
            dokumentValgListe shouldContainKey "BI01S56"
            dokumentValgListe shouldContainKey "BI01S57"
            dokumentValgListe shouldContainKey "BI01S58"
            dokumentValgListe shouldContainKey "BI01S59"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for klage ettergivelse fra bidragspliktig`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.KLAGE,
                behandlingType = Engangsbeløptype.ETTERGIVELSE.name,
                soknadFra = SøktAvType.BIDRAGSPLIKTIG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 6
            dokumentValgListe shouldContainKey "BI01S20"
            dokumentValgListe shouldContainKey "BI01S21"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S60"
            dokumentValgListe shouldContainKey "BI01S64"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }


    @Test
    fun `Skal hente dokumentvalg for BIDRAG med vedtaktype OPPHØR`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.OPPHØR,
                soknadType = "OPPHOR",
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
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
    fun `Skal hente dokumentvalg for BIDRAG med vedtaktype OMGJØRING`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                soknadType = "OMGJORING",
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
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
    fun `Skal hente dokumentvalg for bidrag varsel med soknad type EGET_TILTAK fra NAV_BIDRAG`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.ENDRING,
                soknadType = "EGET_TILTAK",
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 7
            dokumentValgListe shouldContainKey "BI01S06"
            dokumentValgListe shouldContainKey "BI01S07"
            dokumentValgListe shouldContainKey "BI01S35"
            dokumentValgListe shouldContainKey "BI01S46"
            dokumentValgListe shouldContainKey "BI01S62"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for revurdering`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.REVURDERING,
                soknadType = "BEGRENSET_REVURDERING",
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
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
    fun `Skal hente dokumentvalg for klage bidrag uten beregning`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.KLAGE,
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                erFattetBeregnet = false
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01G50"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for klage bidrag med beregning`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.KLAGE,
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.BIDRAGSPLIKTIG,
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
    fun `Skal hente dokumentvalg for vedtak av eget tiltak bidrag - ikke beregnet`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.ENDRING,
                soknadType = "EGET_TILTAK",
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
                erFattetBeregnet = false,
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
    fun `Skal hente dokumentvalg for varsel klage tilbakekreving`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.KLAGE,
                soknadType = "KLAGE",
                behandlingType = Stønadstype.FORSKUDD.name,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                erFattetBeregnet = null,
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 6
            dokumentValgListe shouldContainKey "BI01S20"
            dokumentValgListe shouldContainKey "BI01S21"
            dokumentValgListe shouldContainKey "BI01S60"
            dokumentValgListe shouldContainKey "BI01S64"
            dokumentValgListe shouldContainKey "BI01S02"
            dokumentValgListe shouldContainKey "BI01S10"
        }
    }

    @Test
    fun `Skal hente dokumentvalg for vedtak klage tilbakekreving`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.KLAGE,
                soknadType = "KLAGE",
                behandlingType = Engangsbeløptype.TILBAKEKREVING.name,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                erFattetBeregnet = false,
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
    fun `Skal hente dokumentvalg for vedtak av eget tiltak bidrag - beregnet`() {
        val dokumentValgListe = dokumentValgService!!.hentDokumentMalListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.ENDRING,
                soknadType = "EGET_TILTAK",
                behandlingType = Stønadstype.BIDRAG.name,
                soknadFra = SøktAvType.NAV_BIDRAG,
                erFattetBeregnet = true,
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

    @Test
    fun `Skal hente notater for klage`() {
        val dokumentValgListe = dokumentValgService!!.hentNotatListe(HentDokumentValgRequest(vedtakType = Vedtakstype.KLAGE))

        assertSoftly {
            dokumentValgListe.size shouldBe 5
            dokumentValgListe shouldContainKey "BI01P17"
            dokumentValgListe shouldContainKey "BI01P11"
            dokumentValgListe shouldContainKey "BI01P18"
            dokumentValgListe shouldContainKey "BI01X01"
            dokumentValgListe shouldContainKey "BI01X02"

        }
    }

    @Test
    fun `Skal hente notater for klage med prefiks tittel`() {
        val dokumentValgListe = dokumentValgService!!.hentNotatListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.KLAGE,
                erFattetBeregnet = true,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                stonadType = Stønadstype.EKTEFELLEBIDRAG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 5
            dokumentValgListe["BI01P17"]!!.tittel shouldBe "Ektefellebidrag klage, Uttalelse til klageinstans"
        }
    }

    @Test
    fun `Skal hente notater med prefiks tittel`() {
        val dokumentValgListe = dokumentValgService!!.hentNotatListe(
            HentDokumentValgRequest(
                vedtakType = Vedtakstype.ENDRING,
                erFattetBeregnet = true,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                stonadType = Stønadstype.EKTEFELLEBIDRAG
            )
        )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe["BI01P11"]!!.tittel shouldBe "Ektefellebidrag, NOTAT P11 T"
        }
    }

}
