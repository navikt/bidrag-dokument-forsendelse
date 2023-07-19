package no.nav.bidrag.dokument.forsendelse.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BM
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utils.opprettSak
import no.nav.bidrag.dokument.forsendelse.utils.opprettVedtakDto
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
    lateinit var sakService: SakService

    lateinit var forsendelseTittelService: ForsendelseTittelService

    @BeforeEach
    fun init() {
        forsendelseTittelService = ForsendelseTittelService(
            sakService,
            vedtakConsumer,
        )
        every { sakService.hentSak(any()) } returns opprettSak()
        every { vedtakConsumer.hentVedtak(any()) } returns opprettVedtakDto()
    }

    @Test
    fun `Skal opprette forsendelse tittel for vedtak om bidrag`() {
        val forsendelse = opprettForsendelse2(
            gjelderIdent = GJELDER_IDENT_BM,
            behandlingInfo = BehandlingInfo(
                erFattetBeregnet = true,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                stonadType = StonadType.BIDRAG,
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        val tittel = forsendelseTittelService.opprettForsendelseTittel(forsendelse)

        tittel shouldBe "Vedtak om bidrag til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse tittel for behandlingtype`() {
        val forsendelse = opprettForsendelse2(
            gjelderIdent = GJELDER_IDENT_BM,
            behandlingInfo = BehandlingInfo(
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                erFattetBeregnet = true,
                stonadType = null,
                behandlingType = "AVSKRIVNING",
                vedtakType = VedtakType.FASTSETTELSE
            )
        )
        val tittel = forsendelseTittelService.opprettForsendelseTittel(forsendelse)

        tittel shouldBe "Vedtak om avskrivning til bidragsmottaker"
    }
}