package no.nav.bidrag.dokument.forsendelse.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.api.dto.BehandlingInfoDto
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BA
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BM
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BP
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettEngangsbelopDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utils.opprettSak
import no.nav.bidrag.dokument.forsendelse.utils.opprettStonadsEndringDto
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
class ForsendelseTittelServiceTest {
    @MockkBean
    lateinit var forsendelseTjeneste: ForsendelseTjeneste

    @MockkBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var behandlingConsumer: BidragBehandlingConsumer

    @MockkBean
    lateinit var sakService: SakService

    lateinit var forsendelseTittelService: ForsendelseTittelService

    @BeforeEach
    fun init() {
        forsendelseTittelService =
            ForsendelseTittelService(
                sakService,
                vedtakConsumer,
                behandlingConsumer,
            )
        every { sakService.hentSak(any()) } returns opprettSak()
        every { vedtakConsumer.hentVedtak(any()) } returns opprettVedtakDto()
        every { behandlingConsumer.hentBehandling(any()) } returns opprettBehandlingDto()
    }

    @Test
    fun `Skal opprette forsendelse tittel for vedtak om bidrag`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfoDto(
                            erFattetBeregnet = true,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            stonadType = Stønadstype.BIDRAG,
                            vedtakType = Vedtakstype.FASTSETTELSE,
                        ),
                ),
            )

        tittel shouldBe "Vedtak om barnebidrag til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse tittel for vedtak om bidrag for type klage`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfoDto(
                            erFattetBeregnet = true,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            stonadType = Stønadstype.BIDRAG,
                            vedtakType = Vedtakstype.KLAGE,
                        ),
                ),
            )

        tittel shouldBe "Klagevedtak om barnebidrag til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse tittel for refusjon bidrag`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BP,
                    behandlingInfo =
                        BehandlingInfoDto(
                            erFattetBeregnet = true,
                            soknadFra = SøktAvType.BIDRAGSPLIKTIG,
                            behandlingType = "REFUSJON_BIDRAG",
                            vedtakType = Vedtakstype.FASTSETTELSE,
                        ),
                ),
            )

        tittel shouldBe "Vedtak om refusjon bidrag til bidragspliktig"
    }

    @Test
    fun `Skal opprette forsendelse tittel for reisekostnader`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfoDto(
                            erFattetBeregnet = true,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            behandlingType = "REISEKOSTNADER",
                            vedtakType = Vedtakstype.FASTSETTELSE,
                        ),
                ),
            )

        tittel shouldBe "Vedtak om reisekostnader til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse tittel for vedtak om bidrag til bidragspliktig`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BP,
                    behandlingInfo =
                        BehandlingInfoDto(
                            erFattetBeregnet = true,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            stonadType = Stønadstype.BIDRAG,
                            vedtakType = Vedtakstype.FASTSETTELSE,
                        ),
                ),
            )

        tittel shouldBe "Vedtak om barnebidrag til bidragspliktig"
    }

    @Test
    fun `Skal opprette forsendelse tittel for vedtak om bidrag til barn`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BA,
                    behandlingInfo =
                        BehandlingInfoDto(
                            erFattetBeregnet = true,
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            stonadType = Stønadstype.BIDRAG,
                            vedtakType = Vedtakstype.FASTSETTELSE,
                        ),
                ),
            )

        tittel shouldBe "Vedtak om barnebidrag til barn"
    }

    @Test
    fun `Skal opprette forsendelse tittel for behandlingtype`() {
        val forsendelse =
            opprettForsendelse2(
                gjelderIdent = GJELDER_IDENT_BM,
                behandlingInfo =
                    BehandlingInfo(
                        soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                        erFattetBeregnet = true,
                        stonadType = null,
                        behandlingType = "AVSKRIVNING",
                        vedtakType = Vedtakstype.FASTSETTELSE,
                    ),
            )
        val tittel = forsendelseTittelService.opprettForsendelseTittel(forsendelse)

        tittel shouldBe "Vedtak om avskrivning til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse tittel for varsling av bidrag`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfoDto(
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            erFattetBeregnet = null,
                            stonadType = null,
                            behandlingType = Stønadstype.BIDRAG.name,
                            vedtakType = Vedtakstype.FASTSETTELSE,
                        ),
                ),
            )

        tittel shouldBe "Orientering/Varsel om bidrag til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse tittel for varsling av bidrag for klage`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfoDto(
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            erFattetBeregnet = null,
                            stonadType = null,
                            behandlingType = Stønadstype.BIDRAG.name,
                            vedtakType = Vedtakstype.KLAGE,
                        ),
                ),
            )

        tittel shouldBe "Orientering/Varsel om klage på vedtak om bidrag til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse tittel for vedtak av bidrag 18 år fra vedtakid`() {
        every { vedtakConsumer.hentVedtak(any()) } returns
            opprettVedtakDto().copy(
                type = Vedtakstype.FASTSETTELSE,
                stønadsendringListe =
                    listOf(
                        opprettStonadsEndringDto().copy(type = Stønadstype.BIDRAG18AAR),
                    ),
            )
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfoDto(
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            vedtakId = "123213",
                        ),
                ),
            )

        tittel shouldBe "Vedtak om barnebidrag 18 år til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse tittel for vedtak særtilskudd fra vedtakid`() {
        every { vedtakConsumer.hentVedtak(any()) } returns
            opprettVedtakDto().copy(
                type = Vedtakstype.FASTSETTELSE,
                stønadsendringListe = emptyList(),
                engangsbeløpListe =
                    listOf(
                        opprettEngangsbelopDto().copy(type = Engangsbeløptype.SÆRBIDRAG),
                    ),
            )
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfoDto(
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            vedtakId = "123213",
                            behandlingId = "123123213",
                        ),
                ),
            )

        tittel shouldBe "Vedtak om særtilskudd til bidragsmottaker"
        verify(exactly = 0) { behandlingConsumer.hentBehandling(any()) }
    }

    @Test
    fun `Skal opprette behandling tittel for varsling av bidrag 18 år fra vedtakid`() {
        every { behandlingConsumer.hentBehandling(any()) } returns
            opprettBehandlingDto().copy(
                stønadstype = Stønadstype.EKTEFELLEBIDRAG,
            )
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo =
                        BehandlingInfoDto(
                            soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                            behandlingId = "123213",
                        ),
                ),
            )

        tittel shouldBe "Orientering/Varsel om ektefellebidrag til bidragsmottaker"
    }

    @Test
    fun `Skal ikke opprette forsendelse tittel hvis mangler behandlingInfo`() {
        val tittel =
            forsendelseTittelService.opprettForsendelseTittel(
                OpprettForsendelseForespørsel(
                    enhet = "",
                    saksnummer = "",
                    gjelderIdent = GJELDER_IDENT_BM,
                    behandlingInfo = null,
                ),
            )

        tittel shouldBe null
    }
}
