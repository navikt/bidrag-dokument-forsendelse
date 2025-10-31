package no.nav.bidrag.dokument.forsendelse.service

import StubUtils
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.POJONode
import com.ninjasquad.springmockk.MockkBean
import disableUnleashFeature
import enableUnleashFeature
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import no.nav.bidrag.commons.unleash.UnleashFeaturesProvider
import no.nav.bidrag.dokument.forsendelse.config.UnleashFeatures
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettStonadsEndringDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.ResultatFraVedtakGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VedtakOrkestreringDetaljerGrunnlag
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakPeriodeDto
import no.nav.bidrag.transport.dokument.forsendelse.HentDokumentValgRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

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
        mockkObject(UnleashFeaturesProvider)
        disableUnleashFeature(UnleashFeatures.DOKUMENTVALG_FRA_VEDTAK_BEHANDLING)
        dokumentValgService =
            DokumentValgService(bidragDokumentBestillingConsumer, bidragVedtakConsumer, bidragBehandlingConsumer, tittelService)
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
        val dokumentValgListe =
            dokumentValgService!!.hentDokumentMalListe(
                HentDokumentValgRequest(
                    vedtakType = Vedtakstype.INNKREVING,
                    behandlingType = Engangsbeløptype.SÆRBIDRAG.name,
                    soknadFra = SøktAvType.NAV_BIDRAG,
                    erFattetBeregnet = null,
                ),
            )

        assertSoftly {
            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 2
            fritekstBrev.alternativeTitler shouldContain "Varsel innkreving bidrag til særbidrag"
            fritekstBrev.alternativeTitler shouldContain "Orientering innkreving bidrag til særbidrag"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for innkreving bidrag`() {
        val dokumentValgListe =
            dokumentValgService!!.hentDokumentMalListe(
                HentDokumentValgRequest(
                    vedtakType = Vedtakstype.INNKREVING,
                    behandlingType = Stønadstype.BIDRAG.name,
                    soknadFra = SøktAvType.NAV_BIDRAG,
                    erFattetBeregnet = null,
                ),
            )

        assertSoftly {
            dokumentValgListe.size shouldBe 3
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 4
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
            fritekstBrev.alternativeTitler shouldContain "Informasjon om innkreving av bidragsgjeld"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for varsel av eget tiltak bidrag`() {
        val dokumentValgListe =
            dokumentValgService!!.hentDokumentMalListe(
                HentDokumentValgRequest(
                    vedtakType = Vedtakstype.INNKREVING,
                    soknadType = "EGET_TILTAK",
                    behandlingType = Stønadstype.BIDRAG.name,
                    soknadFra = SøktAvType.NAV_BIDRAG,
                    erFattetBeregnet = null,
                ),
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
        val dokumentValgListe =
            dokumentValgService!!.hentDokumentMalListe(
                HentDokumentValgRequest(
                    vedtakType = Vedtakstype.ENDRING,
                    soknadType = "EGET_TILTAK",
                    behandlingType = Stønadstype.BIDRAG.name,
                    soknadFra = SøktAvType.NAV_BIDRAG,
                    erFattetBeregnet = false,
                ),
            )

        assertSoftly {
            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 1
            fritekstBrev.alternativeTitler shouldContain "Vedtak om offentlig tilbakekreving"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for forskudd søkt av BM`() {
        val dokumentValgListe =
            dokumentValgService!!.hentDokumentMalListe(
                HentDokumentValgRequest(
                    vedtakType = Vedtakstype.ENDRING,
                    behandlingType = Stønadstype.FORSKUDD.name,
                    soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                ),
            )

        assertSoftly {
            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 1
            fritekstBrev.alternativeTitler shouldContain "Orientering om bidragsforskudd"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for søknad til BM eller 18 åring`() {
        assertSoftly("Søknad bidrag fra BM") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.ENDRING,
                        behandlingType = Stønadstype.BIDRAG.name,
                        soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                    ),
                )

            dokumentValgListe.size shouldBe 5
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
        }

        assertSoftly("Søknad bidrag fra 18 år") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.ENDRING,
                        behandlingType = Stønadstype.BIDRAG.name,
                        soknadFra = SøktAvType.BARN_18_ÅR,
                    ),
                )

            dokumentValgListe.size shouldBe 5
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
        }

        assertSoftly("Søknad bidrag 18 år fra BM") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.ENDRING,
                        behandlingType = Stønadstype.BIDRAG18AAR.name,
                        soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                    ),
                )

            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
        }

        assertSoftly("Søknad bidrag 18 år fra 18 åring") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.ENDRING,
                        behandlingType = Stønadstype.BIDRAG18AAR.name,
                        soknadFra = SøktAvType.BARN_18_ÅR,
                    ),
                )

            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for søknad til bidragspliktig`() {
        assertSoftly("Søknad bidrag fra BM") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.ENDRING,
                        behandlingType = Stønadstype.BIDRAG.name,
                        soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                    ),
                )

            dokumentValgListe.size shouldBe 5
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
        }

        assertSoftly("Søknad bidrag fra 18 år") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.ENDRING,
                        behandlingType = Stønadstype.BIDRAG18AAR.name,
                        soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                    ),
                )

            dokumentValgListe.size shouldBe 4
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for innkrevingsgrunnlag`() {
        assertSoftly("Søknad bidrag 18 år") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.INNKREVING,
                        behandlingType = Stønadstype.BIDRAG18AAR.name,
                    ),
                )

            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 4
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
            fritekstBrev.alternativeTitler shouldContain "Informasjon om innkreving av bidragsgjeld"
        }

        assertSoftly("Søknad bidrag") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.INNKREVING,
                        behandlingType = Stønadstype.BIDRAG.name,
                    ),
                )

            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 4
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
            fritekstBrev.alternativeTitler shouldContain "Informasjon om innkreving av bidragsgjeld"
        }

        assertSoftly("Søknad bidrag innkreving fattet") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.INNKREVING,
                        behandlingType = Stønadstype.BIDRAG.name,
                        erFattetBeregnet = true,
                    ),
                )

            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 2
            fritekstBrev.alternativeTitler shouldContain "Orientering om innkreving av fremtidig bidrag"
            fritekstBrev.alternativeTitler shouldContain "Vedtak om innkreving av fremtidig bidrag"
        }

        assertSoftly("Søknad bidrag fra 18 år privat avtale") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        soknadType = "PRIVAT_AVTALE",
                        behandlingType = Stønadstype.BIDRAG18AAR.name,
                        soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                    ),
                )

            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
            fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
            fritekstBrev.alternativeTitler shouldContain "Orientering om trukket søknad"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for særtilskudd`() {
        val dokumentValgListe =
            dokumentValgService!!.hentDokumentMalListe(
                HentDokumentValgRequest(
                    vedtakType = Vedtakstype.FASTSETTELSE,
                    engangsBelopType = Engangsbeløptype.SÆRTILSKUDD,
                ),
            )

        dokumentValgListe.size shouldBe 2
        dokumentValgListe shouldContainKey "BI01S02"
        val fritekstBrev = dokumentValgListe["BI01S02"]!!
        fritekstBrev.alternativeTitler shouldHaveSize 2
        fritekstBrev.alternativeTitler shouldContain "Innkreving orientering til søker"
        fritekstBrev.alternativeTitler shouldContain "Innkreving varsel til motparten"
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for avskrivning`() {
        assertSoftly("Fattet avskrivning") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.FASTSETTELSE,
                        behandlingType = "AVSKRIVNING",
                        erFattetBeregnet = false,
                    ),
                )

            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 2
            fritekstBrev.alternativeTitler shouldContain "Vedtak om avskrivning til bidragspliktig"
            fritekstBrev.alternativeTitler shouldContain "Vedtak om avskrivning til bidragsmottaker"
        }
        assertSoftly("Fattet avskrivning bidragspliktig") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.FASTSETTELSE,
                        behandlingType = "AVSKRIVNING",
                        soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                        erFattetBeregnet = true,
                    ),
                )

            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 3
            fritekstBrev.alternativeTitler shouldContain "Vedtak om innkreving av bidragsgjeld"
            fritekstBrev.alternativeTitler shouldContain "Vedtak om avskrivning til bidragspliktig"
            fritekstBrev.alternativeTitler shouldContain "Vedtak om avskrivning til bidragsmottaker"
        }
        assertSoftly("Varsel avskrivning bidragspliktig") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        vedtakType = Vedtakstype.FASTSETTELSE,
                        behandlingType = "AVSKRIVNING",
                        soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                        erFattetBeregnet = null,
                    ),
                )

            dokumentValgListe.size shouldBe 2
            dokumentValgListe shouldContainKey "BI01S02"
            val fritekstBrev = dokumentValgListe["BI01S02"]!!
            fritekstBrev.alternativeTitler shouldHaveSize 2
            fritekstBrev.alternativeTitler shouldContain "Innkreving av bidragsgjeld, bidragspliktig har bedt om reduksjon"
            fritekstBrev.alternativeTitler shouldContain "Innkreving av gjeld, vi trenger flere opplysninger"
        }
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg for sakomkostninger`() {
        val dokumentValgListe =
            dokumentValgService!!.hentDokumentMalListe(
                HentDokumentValgRequest(
                    vedtakType = Vedtakstype.FASTSETTELSE,
                    behandlingType = "SAKSOMKOSTNINGER",
                    erFattetBeregnet = false,
                ),
            )

        dokumentValgListe.size shouldBe 2
        dokumentValgListe shouldContainKey "BI01S02"
        val fritekstBrev = dokumentValgListe["BI01S02"]!!
        fritekstBrev.alternativeTitler shouldHaveSize 2
        fritekstBrev.alternativeTitler shouldContain "Vedtak om saksomkostninger til bidragspliktig"
        fritekstBrev.alternativeTitler shouldContain "Vedtak om saksomkostninger til bidragsmottaker"
    }

    @Test
    fun `Skal hente titler for dokumentvalg for orkestrert vedtak`() {
        enableUnleashFeature(UnleashFeatures.DOKUMENTVALG_FRA_VEDTAK_BEHANDLING)
        every { bidragVedtakConsumer.hentVedtak(eq("1")) } returns
            opprettVedtakDto()
                .copy(
                    type = Vedtakstype.KLAGE,
                    stønadsendringListe =
                        listOf(
                            opprettStonadsEndringDto().copy(
                                grunnlagReferanseListe = listOf("1"),
                                periodeListe =
                                    listOf(
                                        VedtakPeriodeDto(
                                            ÅrMånedsperiode(LocalDate.parse("2025-01-01"), null),
                                            BigDecimal.ZERO,
                                            null,
                                            "",
                                            null,
                                            listOf("1"),
                                        ),
                                    ),
                            ),
                            opprettStonadsEndringDto().copy(
                                grunnlagReferanseListe = listOf("2"),
                                periodeListe =
                                    listOf(
                                        VedtakPeriodeDto(
                                            ÅrMånedsperiode(LocalDate.parse("2025-01-01"), null),
                                            BigDecimal.ZERO,
                                            null,
                                            "",
                                            null,
                                            listOf("2"),
                                        ),
                                    ),
                            ),
                        ),
                    grunnlagListe =
                        listOf(
                            GrunnlagDto(
                                innhold = ObjectMapper().createObjectNode(),
                                type = Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
                                referanse = "",
                            ),
                            GrunnlagDto(
                                innhold =
                                    POJONode(
                                        VedtakOrkestreringDetaljerGrunnlag(
                                            1,
                                            YearMonth.now(),
                                            YearMonth.now(),
                                        ),
                                    ),
                                type = Grunnlagstype.VEDTAK_ORKESTRERING_DETALJER,
                                referanse = "",
                            ),
                            GrunnlagDto(
                                innhold =
                                    POJONode(
                                        ResultatFraVedtakGrunnlag(
                                            2,
                                            false,
                                        ),
                                    ),
                                type = Grunnlagstype.RESULTAT_FRA_VEDTAK,
                                referanse = "2",
                            ),
                            GrunnlagDto(
                                innhold =
                                    POJONode(
                                        ResultatFraVedtakGrunnlag(
                                            3,
                                            true,
                                        ),
                                    ),
                                type = Grunnlagstype.RESULTAT_FRA_VEDTAK,
                                referanse = "1",
                            ),
                        ),
                )

        every { bidragVedtakConsumer.hentVedtak(eq("2")) } returns
            opprettVedtakDto()
                .copy(
                    type = Vedtakstype.ALDERSJUSTERING,
                    stønadsendringListe =
                        listOf(
                            opprettStonadsEndringDto().copy(
                                grunnlagReferanseListe = listOf("1"),
                            ),
                            opprettStonadsEndringDto().copy(
                                grunnlagReferanseListe = listOf("2"),
                            ),
                        ),
                )

        every { tittelService.hentForsendelseRolle(any()) } returns " til bidragsmottaker"
        val dokumentValgListe =
            dokumentValgService!!.hentDokumentMalListeV2(
                HentDokumentValgRequest(
                    vedtakId = "1",
                    vedtakType = Vedtakstype.KLAGE,
                    behandlingType = "BIDRAG",
                    erFattetBeregnet = true,
                    stonadType = Stønadstype.BIDRAG,
                    soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                ),
            )

        dokumentValgListe.dokumentMalDetaljer.size shouldBe 6
        val dokumentValgListeDokumentMalIder = dokumentValgListe.dokumentMalDetaljer.map { it.key }
        dokumentValgListeDokumentMalIder.shouldContain("BI01B50")
        dokumentValgListeDokumentMalIder.shouldContain(brevkodeAldersjustering)
        dokumentValgListeDokumentMalIder.shouldContain(brevkodeForsideVedtak)

        dokumentValgListe.automatiskOpprettDokumenter.size shouldBe 3
        val automatiskeOpprettetDokumentMalIder = dokumentValgListe.automatiskOpprettDokumenter.map { it.malId }
        automatiskeOpprettetDokumentMalIder.shouldContain("BI01B50")
        automatiskeOpprettetDokumentMalIder.shouldContain(brevkodeAldersjustering)
        automatiskeOpprettetDokumentMalIder.shouldContain(brevkodeForsideVedtak)
        dokumentValgListe.automatiskOpprettDokumenter.find { it.malId == brevkodeForsideVedtak }!!.tittel shouldBe "Klagevedtak om barnebidrag til bidragsmottaker (forside)"
    }

    @Test
    fun `Skal hente alternative titler for dokumentvalg tilbakekreving`() {
        assertSoftly("Varsling tilbakekreving") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        behandlingType = Engangsbeløptype.TILBAKEKREVING.name,
                        soknadFra = SøktAvType.NAV_BIDRAG,
                        erFattetBeregnet = null,
                    ),
                )

            assertSoftly {
                dokumentValgListe.size shouldBe 2
                dokumentValgListe shouldContainKey "BI01S02"
                val fritekstBrev = dokumentValgListe["BI01S02"]!!
                fritekstBrev.alternativeTitler shouldHaveSize 1
                fritekstBrev.alternativeTitler shouldContain "Varsel om mulig tilbakekreving av feilutbetalt bidrag"
            }
        }

        assertSoftly("Fattet vedtak tilbakekreving") {
            val dokumentValgListe =
                dokumentValgService!!.hentDokumentMalListe(
                    HentDokumentValgRequest(
                        behandlingType = Engangsbeløptype.TILBAKEKREVING.name,
                        soknadFra = SøktAvType.NAV_BIDRAG,
                        erFattetBeregnet = true,
                    ),
                )

            assertSoftly {
                dokumentValgListe.size shouldBe 2
                dokumentValgListe shouldContainKey "BI01S02"
                val fritekstBrev = dokumentValgListe["BI01S02"]!!
                fritekstBrev.alternativeTitler shouldHaveSize 1
                fritekstBrev.alternativeTitler shouldContain "Vedtak om tilbakekreving etter direktebetaling"
            }
        }
    }
}
