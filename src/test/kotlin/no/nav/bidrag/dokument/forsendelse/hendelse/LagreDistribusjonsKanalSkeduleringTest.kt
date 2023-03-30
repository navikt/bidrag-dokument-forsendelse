package no.nav.bidrag.dokument.forsendelse.hendelse

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.forsendelse.TestContainerRunner
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@TestPropertySource(properties = ["LAGRE_DIST_INFO_PAGE_SIZE=10"])
class LagreDistribusjonsKanalSkeduleringTest : TestContainerRunner() {
    @Autowired
    private lateinit var skedulering: ForsendelseSkedulering

    @BeforeEach
    fun setupMocks() {
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
        stubUtils.stubHentDistribusjonInfo()
    }

    private fun opprettIkkeDistribuertForsendelse(): Forsendelse {
        return testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.UNDER_PRODUKSJON,
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentMalId = "MAL1"
                    )
                )
            )
        )
    }

    private fun opprettDistribuertForsendelse(distTidspunktMinusHours: Long): Forsendelse {
        return testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.DISTRIBUERT,
                distribusjonsTidspunkt = LocalDateTime.now().minusHours(distTidspunktMinusHours),
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentMalId = "MAL1"
                    )
                ),
                arkivJournalpostId = (10000..20000).random().toString()
            )
        )
    }

    @Test
    fun `skal lagre distribusjoninfo`() {
        val forsendelseNy = opprettDistribuertForsendelse(1).forsendelseId!!
        val forsendelseNavNo = opprettDistribuertForsendelse(5).forsendelseId!!
        val forsendelseSDP = opprettDistribuertForsendelse(2)
        val forsendelseSentralPrint = opprettDistribuertForsendelse(2)
        opprettDistribuertForsendelse(8)
        opprettDistribuertForsendelse(7)
        opprettIkkeDistribuertForsendelse()
        opprettIkkeDistribuertForsendelse()
        opprettIkkeDistribuertForsendelse()
        opprettIkkeDistribuertForsendelse()
        opprettIkkeDistribuertForsendelse()

        stubUtils.stubHentDistribusjonInfo(forsendelseSDP.journalpostIdFagarkiv, DistribusjonKanal.SDP.name)
        stubUtils.stubHentDistribusjonInfo(forsendelseSentralPrint.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)

        skedulering.lagreDistribusjoninfo()

        stubUtils.Valider().hentDistribusjonInfoKalt(5)

        assertSoftly {
            testDataManager.hentForsendelse(forsendelseNy)?.distribusjonKanal shouldBe null
            testDataManager.hentForsendelse(forsendelseNavNo)?.distribusjonKanal shouldBe DistribusjonKanal.NAV_NO
            testDataManager.hentForsendelse(forsendelseSDP.forsendelseId!!)?.distribusjonKanal shouldBe DistribusjonKanal.SDP
            testDataManager.hentForsendelse(forsendelseSentralPrint.forsendelseId!!)?.distribusjonKanal shouldBe DistribusjonKanal.SENTRAL_UTSKRIFT
        }
    }

    @Test
    fun `skal ikke feile hvis lagring av distribusjoninfo for en forsendelse feiler`() {
        val forsendelseNavNo = opprettDistribuertForsendelse(5).forsendelseId!!
        val forsendelseSDP = opprettDistribuertForsendelse(2)
        val forsendelseSentralPrint = opprettDistribuertForsendelse(2)
        val foresendelseFeil = opprettDistribuertForsendelse(8)
        opprettDistribuertForsendelse(7)
        opprettIkkeDistribuertForsendelse()
        opprettIkkeDistribuertForsendelse()
        opprettIkkeDistribuertForsendelse()
        opprettIkkeDistribuertForsendelse()
        opprettIkkeDistribuertForsendelse()

        stubUtils.stubHentDistribusjonInfo(forsendelseSDP.journalpostIdFagarkiv, DistribusjonKanal.SDP.name)
        stubUtils.stubHentDistribusjonInfo(forsendelseSentralPrint.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
        stubUtils.stubHentDistribusjonInfo(
            foresendelseFeil.journalpostIdFagarkiv,
            DistribusjonKanal.SENTRAL_UTSKRIFT.name,
            HttpStatus.INTERNAL_SERVER_ERROR
        )

        skedulering.lagreDistribusjoninfo()

        // Pga retry så blir endepunktet kalt flere ganger
        stubUtils.Valider().hentDistribusjonInfoKalt(7)

        assertSoftly {
            testDataManager.hentForsendelse(forsendelseNavNo)?.distribusjonKanal shouldBe DistribusjonKanal.NAV_NO
            testDataManager.hentForsendelse(forsendelseSDP.forsendelseId!!)?.distribusjonKanal shouldBe DistribusjonKanal.SDP
            testDataManager.hentForsendelse(forsendelseSentralPrint.forsendelseId!!)?.distribusjonKanal shouldBe DistribusjonKanal.SENTRAL_UTSKRIFT
            testDataManager.hentForsendelse(foresendelseFeil.forsendelseId!!)?.distribusjonKanal shouldBe null
        }
    }

    @Test
    fun `skal ikke prosessere forsendelser mer enn sidestørrelse`() {
        opprettDistribuertForsendelse(5)
        opprettDistribuertForsendelse(2)
        opprettDistribuertForsendelse(2)
        opprettDistribuertForsendelse(8)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)
        opprettDistribuertForsendelse(3)

        skedulering.lagreDistribusjoninfo()

        stubUtils.Valider().hentDistribusjonInfoKalt(10)
    }
}
