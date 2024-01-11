package no.nav.bidrag.dokument.forsendelse.hendelse

import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.forsendelse.TestContainerRunner
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.ForsendelseMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_2
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@TestPropertySource(properties = ["LAGRE_DIST_INFO_PAGE_SIZE=10"])
@Transactional
class LagreDistribusjonsKanalSkeduleringTest : TestContainerRunner() {
    @Autowired
    private lateinit var skedulering: ForsendelseSkedulering

    @BeforeEach
    fun setupMocks() {
        WireMock.resetAllRequests()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
        stubUtils.stubHentDistribusjonInfo()
    }

    private fun opprettIkkeDistribuertForsendelse(): Forsendelse {
        return testDataManager.lagreForsendelseNotNewTransaction(
            opprettForsendelse2(
                status = ForsendelseStatus.UNDER_PRODUKSJON,
                dokumenter =
                    listOf(
                        nyttDokument(
                            dokumentreferanseOriginal = null,
                            journalpostId = null,
                            dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                            tittel = "FORSENDELSE 1",
                            arkivsystem = DokumentArkivSystem.JOARK,
                            dokumentMalId = DOKUMENTMAL_UTGÅENDE_2,
                        ),
                    ),
            ),
        )
    }

    private fun opprettDistribuertForsendelse(
        distTidspunktMinusHours: Long,
        kanal: DistribusjonKanal? = null,
        markerSjekketForRedistribusjon: Boolean = false,
    ): Forsendelse {
        return testDataManager.lagreForsendelseNotNewTransaction(
            opprettForsendelse2(
                status = ForsendelseStatus.DISTRIBUERT,
                distribusjonsTidspunkt = LocalDateTime.now().minusHours(distTidspunktMinusHours),
                kanal = kanal,
                dokumenter =
                    listOf(
                        nyttDokument(
                            dokumentreferanseOriginal = null,
                            journalpostId = null,
                            dokumentStatus = DokumentStatus.FERDIGSTILT,
                            tittel = "FORSENDELSE 1",
                            arkivsystem = DokumentArkivSystem.JOARK,
                            dokumentMalId = DOKUMENTMAL_UTGÅENDE_2,
                        ),
                    ),
                arkivJournalpostId = (10000..20000).random().toString(),
                metadata =
                    if (markerSjekketForRedistribusjon) {
                        run {
                            val metadata = ForsendelseMetadataDo()
                            metadata.markerSomSjekketNavNoRedistribusjon()
                            metadata
                        }
                    } else {
                        null
                    },
            ),
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
            testDataManager.hentForsendelse(
                forsendelseSentralPrint.forsendelseId!!,
            )?.distribusjonKanal shouldBe DistribusjonKanal.SENTRAL_UTSKRIFT
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
            HttpStatus.INTERNAL_SERVER_ERROR,
        )

        skedulering.lagreDistribusjoninfo()

        // Pga retry så blir endepunktet kalt flere ganger
        stubUtils.Valider().hentDistribusjonInfoKalt(7)

        assertSoftly {
            testDataManager.hentForsendelse(forsendelseNavNo)?.distribusjonKanal shouldBe DistribusjonKanal.NAV_NO
            testDataManager.hentForsendelse(forsendelseNavNo)?.bestiltNyDistribusjon shouldBe false
            testDataManager.hentForsendelse(forsendelseSDP.forsendelseId!!)?.bestiltNyDistribusjon shouldBe false
            testDataManager.hentForsendelse(forsendelseSDP.forsendelseId!!)?.distribusjonKanal shouldBe DistribusjonKanal.SDP
            testDataManager.hentForsendelse(
                forsendelseSentralPrint.forsendelseId!!,
            )?.distribusjonKanal shouldBe DistribusjonKanal.SENTRAL_UTSKRIFT
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

    @Nested
    inner class ResynkDistribusjonkanalNAVNOTest {
        @Test
        fun `skal resynke distribusjoninfo til sentral distribusjon`() {
            val forsendelseNavNo1 = opprettDistribuertForsendelse(48, kanal = DistribusjonKanal.NAV_NO)
            val forsendelseNavNo2 = opprettDistribuertForsendelse(23, kanal = DistribusjonKanal.NAV_NO)
            val forsendelseNavNo3 = opprettDistribuertForsendelse(55, kanal = DistribusjonKanal.NAV_NO)
            val forsendelseNavNo4 = opprettDistribuertForsendelse(60, kanal = DistribusjonKanal.NAV_NO)
            val forsendelseSentralUtskrift4 = opprettDistribuertForsendelse(60, kanal = DistribusjonKanal.SENTRAL_UTSKRIFT)
            opprettDistribuertForsendelse(8)
            opprettDistribuertForsendelse(7)
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()

            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo1.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo2.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo3.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo4.journalpostIdFagarkiv, DistribusjonKanal.NAV_NO.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseSentralUtskrift4.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)

            skedulering.resynkDistribusjoninfoNavNo()

            stubUtils.Valider().hentDistribusjonInfoKalt(3)

            assertSoftly("Forsendelse 1") {
                val forsendelse = testDataManager.hentForsendelse(forsendelseNavNo1.forsendelseId!!)
                forsendelse?.distribusjonKanal shouldBe DistribusjonKanal.SENTRAL_UTSKRIFT
                forsendelse?.bestiltNyDistribusjon shouldBe true
                forsendelse?.metadata?.harSjekketForNavNoRedistribusjon() shouldBe true
            }

            assertSoftly("Forsendelse 2") {
                val forsendelse = testDataManager.hentForsendelse(forsendelseNavNo2.forsendelseId!!)
                forsendelse?.distribusjonKanal shouldBe DistribusjonKanal.NAV_NO
                forsendelse?.bestiltNyDistribusjon shouldBe false
                forsendelse?.metadata?.harSjekketForNavNoRedistribusjon() shouldBe null
            }

            assertSoftly("Forsendelse 3") {
                val forsendelse = testDataManager.hentForsendelse(forsendelseNavNo3.forsendelseId!!)
                forsendelse?.distribusjonKanal shouldBe DistribusjonKanal.SENTRAL_UTSKRIFT
                forsendelse?.bestiltNyDistribusjon shouldBe true
                forsendelse?.metadata?.harSjekketForNavNoRedistribusjon() shouldBe true
            }

            assertSoftly("Forsendelse 4") {
                val forsendelse = testDataManager.hentForsendelse(forsendelseNavNo4.forsendelseId!!)
                forsendelse?.distribusjonKanal shouldBe DistribusjonKanal.NAV_NO
                forsendelse?.bestiltNyDistribusjon shouldBe false
                forsendelse?.metadata?.harSjekketForNavNoRedistribusjon() shouldBe true
            }

            assertSoftly("Forsendelse 5") {
                val forsendelse = testDataManager.hentForsendelse(forsendelseSentralUtskrift4.forsendelseId!!)
                forsendelse?.distribusjonKanal shouldBe DistribusjonKanal.SENTRAL_UTSKRIFT
                forsendelse?.bestiltNyDistribusjon shouldBe false
                forsendelse?.metadata?.harSjekketForNavNoRedistribusjon() shouldBe null
            }
        }

        @Test
        fun `skal ikke hente forsendelse som er markert som sjekket for redistribusjon`() {
            val forsendelseNavNo1 = opprettDistribuertForsendelse(48, kanal = DistribusjonKanal.NAV_NO, true)
            val forsendelseNavNo2 = opprettDistribuertForsendelse(44, kanal = DistribusjonKanal.NAV_NO, true)
            val forsendelseNavNo3 = opprettDistribuertForsendelse(55, kanal = DistribusjonKanal.NAV_NO)
            val forsendelseNavNo4 = opprettDistribuertForsendelse(60, kanal = DistribusjonKanal.NAV_NO)
            opprettDistribuertForsendelse(8)
            opprettDistribuertForsendelse(7)
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()

            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo1.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo2.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo3.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo4.journalpostIdFagarkiv, DistribusjonKanal.NAV_NO.name)

            skedulering.resynkDistribusjoninfoNavNo()

            stubUtils.Valider().hentDistribusjonInfoKalt(2)
            stubUtils.Valider().hentDistribusjonInfoKaltMed(forsendelseNavNo3.journalpostIdFagarkiv)
            stubUtils.Valider().hentDistribusjonInfoKaltMed(forsendelseNavNo4.journalpostIdFagarkiv)
        }

        @Test
        fun `skal sjekke for distribusjon i bestemt tidsvindu`() {
            val forsendelseNavNo1 = opprettDistribuertForsendelse(8, kanal = DistribusjonKanal.NAV_NO, true)
            val forsendelseNavNo2 = opprettDistribuertForsendelse(8, kanal = DistribusjonKanal.NAV_NO, true)
            val forsendelseNavNo3 = opprettDistribuertForsendelse(5, kanal = DistribusjonKanal.NAV_NO)
            val forsendelseNavNo4 = opprettDistribuertForsendelse(10, kanal = DistribusjonKanal.NAV_NO)
            val forsendelseNavNo5 = opprettDistribuertForsendelse(12, kanal = DistribusjonKanal.NAV_NO)
            val forsendelseNavNo6 = opprettDistribuertForsendelse(3, kanal = DistribusjonKanal.NAV_NO)
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()
            opprettIkkeDistribuertForsendelse()

            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo1.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo2.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo3.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo4.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo5.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)
            stubUtils.stubHentDistribusjonInfo(forsendelseNavNo6.journalpostIdFagarkiv, DistribusjonKanal.SENTRAL_UTSKRIFT.name)

            skedulering.resynkDistribusjoninfoNavNo(
                afterDate = LocalDateTime.now().minusHours(11),
                beforeDate = LocalDateTime.now().minusHours(4),
            )

            stubUtils.Valider().hentDistribusjonInfoKalt(2)
            stubUtils.Valider().hentDistribusjonInfoKaltMed(forsendelseNavNo3.journalpostIdFagarkiv)
            stubUtils.Valider().hentDistribusjonInfoKaltMed(forsendelseNavNo4.journalpostIdFagarkiv)
        }
    }
}
