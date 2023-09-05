package no.nav.bidrag.dokument.forsendelse.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Ordering
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.api.dto.BehandlingInfoDto
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragPersonConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BP
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.utils.opprettBehandlingDto
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utils.opprettSak
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType
import no.nav.bidrag.domain.ident.PersonIdent
import no.nav.bidrag.transport.person.PersonDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
class OpprettForsendelseServiceTest {

    @MockkBean
    lateinit var forsendelseTjeneste: ForsendelseTjeneste

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean
    lateinit var dokumentBestillingService: DokumentBestillingService

    @MockkBean
    lateinit var personConsumer: BidragPersonConsumer

    @MockkBean
    lateinit var dokumenttjeneste: DokumentTjeneste

    @MockkBean
    lateinit var saksbehandlerInfoManager: SaksbehandlerInfoManager

    @MockkBean
    lateinit var vedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var behandlingConsumer: BidragBehandlingConsumer

    @MockkBean
    lateinit var sakService: SakService

    @MockkBean
    lateinit var forsendelseTittelService: ForsendelseTittelService
    var opprettForsendelseService: OpprettForsendelseService? = null

    @BeforeEach
    fun init() {
        opprettForsendelseService = OpprettForsendelseService(
            tilgangskontrollService,
            dokumentBestillingService,
            forsendelseTjeneste,
            personConsumer,
            dokumenttjeneste,
            saksbehandlerInfoManager,
            forsendelseTittelService
        )
        every { tilgangskontrollService.sjekkTilgangSak(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangPerson(any()) } returns Unit
        every { saksbehandlerInfoManager.hentSaksbehandler() } returns Saksbehandler(SAKSBEHANDLER_IDENT, SAKSBEHANDLER_NAVN)
        every { personConsumer.hentPerson(any()) } returns PersonDto(PersonIdent(GJELDER_IDENT))
        every { forsendelseTittelService.opprettForsendelseTittel(any<OpprettForsendelseForespørsel>()) } returns "Vedtak om bidrag"
        every { dokumenttjeneste.opprettNyttDokument(any(), any<List<OpprettDokumentForespørsel>>()) } returns emptyList()
        every { forsendelseTjeneste.lagre(any()) } returns opprettForsendelse2()
        every { dokumentBestillingService.hentDokumentmalDetaljer() } returns emptyMap()
        every { sakService.hentSak(any()) } returns opprettSak()
        every { vedtakConsumer.hentVedtak(any()) } returns opprettVedtakDto()
        every { behandlingConsumer.hentBehandling(any()) } returns opprettBehandlingDto()
    }

    @Test
    fun `Skal opprette forsendelse med behandlingtype`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = emptyList(),
            behandlingInfo = BehandlingInfoDto(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                stonadType = null,
                behandlingType = "AVSKRIVNING",
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel)
        verify {
            forsendelseTjeneste.lagre(
                withArg {
                    it.behandlingInfo!!.behandlingType shouldBe "AVSKRIVNING"
                    it.behandlingInfo!!.stonadType shouldBe null
                    it.behandlingInfo!!.engangsBelopType shouldBe null
                }
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse med soknadtype`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = emptyList(),
            behandlingInfo = BehandlingInfoDto(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                soknadType = "EGET_TILTAK",
                behandlingType = "AVSKRIVNING",
                vedtakType = VedtakType.ENDRING
            )
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel)
        verify {
            forsendelseTjeneste.lagre(
                withArg {
                    it.behandlingInfo!!.behandlingType shouldBe "AVSKRIVNING"
                    it.behandlingInfo!!.soknadType shouldBe "EGET_TILTAK"
                    it.behandlingInfo!!.stonadType shouldBe null
                    it.behandlingInfo!!.engangsBelopType shouldBe null
                }
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse med forsendelse tittel (skal ikke opprette tittel)`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
                )
            ),
            opprettTittel = true,
            gjelderIdent = GJELDER_IDENT_BP,
            behandlingInfo = BehandlingInfoDto(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                stonadType = null,
                behandlingType = StonadType.BIDRAG.name,
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel)
        verify {
            forsendelseTjeneste.lagre(
                withArg {
//                    it.tittel shouldBe "Vedtak om bidrag"
                    it.tittel shouldBe null
                }
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse med status under opprettelse hvis det ikke er noe dokumenter`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = emptyList(),
            opprettTittel = true,
            gjelderIdent = GJELDER_IDENT_BP,
            behandlingInfo = BehandlingInfoDto(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                stonadType = null,
                behandlingType = StonadType.BIDRAG.name,
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel)
        verify {
            forsendelseTjeneste.lagre(
                withArg {
                    it.status shouldBe ForsendelseStatus.UNDER_OPPRETTELSE
                }
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse med status under produksjon hvis det opprettes med dokumenter`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
                )
            ),
            opprettTittel = true,
            gjelderIdent = GJELDER_IDENT_BP,
            behandlingInfo = BehandlingInfoDto(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                stonadType = null,
                behandlingType = StonadType.BIDRAG.name,
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel)
        verify {
            forsendelseTjeneste.lagre(
                withArg {
                    it.status shouldBe ForsendelseStatus.UNDER_PRODUKSJON
                }
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse uten forsendelse tittel`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
                )
            ),
            behandlingInfo = BehandlingInfoDto(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                stonadType = null,
                behandlingType = StonadType.EKTEFELLEBIDRAG.name,
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel)
        verify {
            forsendelseTjeneste.lagre(
                withArg {
                    it.tittel shouldBe null
                }
            )
        }
    }

    @Test
    fun `Skal ikke lagre forsendelse tittel for notat men legge til prefiks på dokument tittel`() {
        every { forsendelseTittelService.opprettForsendelseBehandlingPrefiks(any()) } returns "Ektefellebidrag"
        every { dokumentBestillingService.hentDokumentmalDetaljer() } returns mapOf(
            DOKUMENTMAL_NOTAT to DokumentMalDetaljer(
                "Tittel notat",
                DokumentMalType.NOTAT
            )
        )
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = "Tittel notat",
                    dokumentmalId = DOKUMENTMAL_NOTAT
                )
            ),
            behandlingInfo = BehandlingInfoDto(
                behandlingType = StonadType.EKTEFELLEBIDRAG.name
            ),
            opprettTittel = true
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel)
        verify {
            forsendelseTjeneste.lagre(
                withArg {
                    it.tittel shouldBe null
                }
            )
            dokumenttjeneste.opprettNyttDokument(
                any<Forsendelse>(),
                withArg<List<OpprettDokumentForespørsel>> {
                    it[0].tittel shouldBe "Ektefellebidrag, Tittel notat"
                }
            )
        }
    }

    @Test
    fun `Skal ikke legge til prefiks på dokument tittel hvis opprettTittel er false`() {
        every { forsendelseTittelService.opprettForsendelseBehandlingPrefiks(any()) } returns "Ektefellebidrag"
        every { dokumentBestillingService.hentDokumentmalDetaljer() } returns mapOf(
            DOKUMENTMAL_NOTAT to DokumentMalDetaljer(
                "Tittel notat",
                DokumentMalType.NOTAT
            )
        )
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = "Tittel notat",
                    dokumentmalId = DOKUMENTMAL_NOTAT
                )
            ),
            behandlingInfo = BehandlingInfoDto(
                behandlingType = StonadType.EKTEFELLEBIDRAG.name
            ),
            opprettTittel = false
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel)
        verify {
            forsendelseTjeneste.lagre(
                withArg {
                    it.tittel shouldBe null
                }
            )
            dokumenttjeneste.opprettNyttDokument(
                any<Forsendelse>(),
                withArg<List<OpprettDokumentForespørsel>> {
                    it[0].tittel shouldBe "Tittel notat"
                }
            )
        }
    }

    @Test
    fun `Skal ikke lagre behandlingtype hvis stonadType eller engangsbeloptype finnes`() {
        val opprettForsendelseForespørselStonad = nyOpprettForsendelseForespørsel().copy(
            dokumenter = emptyList(),
            behandlingInfo = BehandlingInfoDto(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                stonadType = StonadType.BIDRAG,
                behandlingType = "AVSKRIVNING",
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        val opprettForsendelseForespørselEngangsbelop = nyOpprettForsendelseForespørsel().copy(
            dokumenter = emptyList(),
            behandlingInfo = BehandlingInfoDto(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                engangsBelopType = EngangsbelopType.SAERTILSKUDD,
                behandlingType = "AVSKRIVNING",
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørselStonad)
        opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørselEngangsbelop)

        verify(ordering = Ordering.SEQUENCE) {
            forsendelseTjeneste.lagre(
                withArg {
                    it.behandlingInfo!!.behandlingType shouldBe null
                    it.behandlingInfo!!.stonadType shouldBe StonadType.BIDRAG
                    it.behandlingInfo!!.engangsBelopType shouldBe null
                }
            )
            forsendelseTjeneste.lagre(
                withArg {
                    it.behandlingInfo!!.behandlingType shouldBe null
                    it.behandlingInfo!!.stonadType shouldBe null
                    it.behandlingInfo!!.engangsBelopType shouldBe EngangsbelopType.SAERTILSKUDD
                }
            )
        }
    }

    @Test
    fun `Skal feile hvis dokumentdato er senere enn dagens dato`() {
        every { forsendelseTittelService.opprettForsendelseBehandlingPrefiks(any()) } returns "Ektefellebidrag"
        every { dokumentBestillingService.hentDokumentmalDetaljer() } returns mapOf(
            DOKUMENTMAL_NOTAT to DokumentMalDetaljer(
                "Tittel notat",
                DokumentMalType.NOTAT
            )
        )
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = "Tittel notat",
                    dokumentmalId = DOKUMENTMAL_NOTAT,
                    dokumentDato = LocalDateTime.now().plusDays(1)
                )
            )
        )
        val result = shouldThrow<UgyldigForespørsel> { opprettForsendelseService!!.opprettForsendelse(opprettForsendelseForespørsel) }
        result.message shouldBe "Dokumentdato kan ikke være senere enn dagens dato"
    }
}
