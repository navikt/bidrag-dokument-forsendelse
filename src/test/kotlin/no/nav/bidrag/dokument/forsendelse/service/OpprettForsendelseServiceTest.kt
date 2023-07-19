package no.nav.bidrag.dokument.forsendelse.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.Ordering
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.behandling.felles.enums.EngangsbelopType
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.api.dto.BehandlingInfoDto
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.BidragPersonConsumer
import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.domain.ident.PersonIdent
import no.nav.bidrag.transport.person.PersonDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

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
            forsendelseTjeneste.lagre(withArg {
                it.behandlingInfo!!.behandlingType shouldBe "AVSKRIVNING"
                it.behandlingInfo!!.stonadType shouldBe null
                it.behandlingInfo!!.engangsBelopType shouldBe null
            })
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
            forsendelseTjeneste.lagre(withArg {
                it.behandlingInfo!!.behandlingType shouldBe null
                it.behandlingInfo!!.stonadType shouldBe StonadType.BIDRAG
                it.behandlingInfo!!.engangsBelopType shouldBe null
            })
            forsendelseTjeneste.lagre(withArg {
                it.behandlingInfo!!.behandlingType shouldBe null
                it.behandlingInfo!!.stonadType shouldBe null
                it.behandlingInfo!!.engangsBelopType shouldBe EngangsbelopType.SAERTILSKUDD
            })
        }
    }

}