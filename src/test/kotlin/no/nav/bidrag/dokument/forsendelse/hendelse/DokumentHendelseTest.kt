package no.nav.bidrag.dokument.forsendelse.hendelse

import com.ninjasquad.springmockk.SpykBean
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.verify
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.DokumentHendelse
import no.nav.bidrag.dokument.dto.DokumentHendelseType
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.dto.JournalpostStatus
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.er
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class DokumentHendelseTest : KafkaHendelseTestRunner() {

    @SpykBean
    private lateinit var dokumentTjeneste: DokumentTjeneste

    @SpykBean
    private lateinit var journalpostHendelseProdusent: JournalpostKafkaHendelseProdusent


    @BeforeEach
    fun resetSpys() {
        clearAllMocks()
    }

    private fun opprettHendelse(dokumentreferanse: String, status: DokumentStatusDto = DokumentStatusDto.UNDER_REDIGERING): DokumentHendelse {
        return DokumentHendelse(
            dokumentreferanse = dokumentreferanse,
            arkivSystem = DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER,
            hendelseType = DokumentHendelseType.ENDRING,
            sporingId = "sporing",
            status = status
        )
    }

    @Test
    fun `Skal oppdatere status på dokument til UNDER_REDIGERING ved mottatt hendelse`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(
                dokumentreferanseOriginal = null,
                journalpostId = null,
                dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                tittel = "FORSENDELSE 1",
                arkivsystem = DokumentArkivSystem.UKJENT
            )
        }
        val dokument = forsendelse.dokumenter[0]
        val hendelse = opprettHendelse(dokument.dokumentreferanse, status = DokumentStatusDto.UNDER_REDIGERING)
        sendMeldingTilDokumentHendelse(hendelse)

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
            val dokumentEtter = forsendelseEtter.dokumenter[0]
            dokumentEtter.dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING
            dokumentEtter.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
            verify(exactly = 0) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }
    }

    @Test
    fun `Skal oppdatere status på alle dokumenter til FERDIGSTILT ved mottatt hendelse`() {
        val dokumentreferanse = "13213123123"
        val forsendelse1 = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(
                dokumentreferanseOriginal = dokumentreferanse,
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "FORSENDELSE 1",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
            )
        }

        val forsendelse2 = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(
                dokumentreferanseOriginal = dokumentreferanse,
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "FORSENDELSE 2",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
            )
        }
        val hendelse = opprettHendelse(dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelse)

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse1Etter = testDataManager.hentForsendelse(forsendelse1.forsendelseId!!)!!
            val forsendelse2Etter = testDataManager.hentForsendelse(forsendelse2.forsendelseId!!)!!
            val dokument1Etter = forsendelse1Etter.dokumenter[0]
            val dokument2Etter = forsendelse2Etter.dokumenter[0]
            dokument1Etter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument2Etter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT

            verify(exactly = 2) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }
    }

    @Test
    fun `Skal sende journalpost hendelse når alle dokumenter i forsendelse er ferdigstilt`() {
        val forsendelse1 = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "DOK1",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                rekkefølgeIndeks = 0,
                dokumentreferanseOriginal = null,
                journalpostId = null
            )
            +nyttDokument(
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "DOK2",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                rekkefølgeIndeks = 1,
                dokumentreferanseOriginal = null,
                journalpostId = null
            )
        }
        val hendelseDok1 = opprettHendelse(forsendelse1.dokumenter[0].dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelseDok1)

        val hendelseDok2 = opprettHendelse(forsendelse1.dokumenter[1].dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelseDok2)

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse1.forsendelseId!!)!!
            val dokument1Etter = forsendelseEtter.dokumenter[0]
            val dokument2Etter = forsendelseEtter.dokumenter[1]
            dokument1Etter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument2Etter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT

            verify(exactly = 1) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }

        val hendelse = readFromJournalpostTopic()
        hendelse shouldNotBe null
        hendelse!!.status shouldBe JournalpostStatus.KLAR_FOR_DISTRIBUSJON.name
        hendelse.journalpostId shouldBe forsendelse1.forsendelseIdMedPrefix
        hendelse.tema shouldBe forsendelse1.tema.name
        hendelse.enhet shouldBe forsendelse1.enhet
        hendelse.tittel shouldBe forsendelse1.dokumenter.hoveddokument?.tittel
        hendelse.fnr shouldBe forsendelse1.gjelderIdent
        hendelse.journalposttype shouldBe JournalpostType.UTGÅENDE.name
        hendelse.sakstilknytninger!! shouldContain forsendelse1.saksnummer
    }

    @Test
    fun `Skal ikke sende journalpost hendelse når ikke alle dokumenter i forsendelse er ferdigstilt`() {
        val forsendelse1 = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "DOK1",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                rekkefølgeIndeks = 0,
                dokumentreferanseOriginal = null,
                journalpostId = null
            )
            +nyttDokument(
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "DOK2",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                rekkefølgeIndeks = 1,
                dokumentreferanseOriginal = null,
                journalpostId = null
            )
        }
        val hendelseDok1 = opprettHendelse(forsendelse1.dokumenter[0].dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelseDok1)

        await.pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).untilAsserted {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse1.forsendelseId!!)!!
            val dokument1Etter = forsendelseEtter.dokumenter[0]
            val dokument2Etter = forsendelseEtter.dokumenter[1]
            dokument1Etter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument2Etter.dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING

            verify(exactly = 0) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }

        val hendelse = readFromJournalpostTopic()
        hendelse shouldBe null
    }

    @Test
    fun `Skal ignorere hendelse hvis ingen forsendelse med dokumentreferanse finnes`() {
        val dokumentreferanse = "4575475679769679769679679769"
        val hendelse = opprettHendelse(dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelse)

        await.pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            verify(exactly = 1) { dokumentTjeneste.hentDokumenterMedReferanse(dokumentreferanse) }
        }
    }

    @Test
    fun `Skal ferdigstille og arkivere forsendelse med type notat når dokument er ferdigstilt`() {
        val nyJournalpostId = "1331234412321"
        stubUtils.stubHentDokument()
        stubUtils.stubOpprettJournalpost(nyJournalpostId)
        val forsendelseNotat = testDataManager.opprettOgLagreForsendelse {
            er notat true
            +nyttDokument(
                dokumentreferanseOriginal = null,
                journalpostId = null,
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "Forsendelse notat",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
            )
        }

        val hendelse = opprettHendelse(forsendelseNotat.dokumenter[0].dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelse)

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelseNotat.forsendelseId!!)!!

            forsendelseEtter.status shouldBe ForsendelseStatus.FERDIGSTILT
            forsendelseEtter.journalpostIdFagarkiv shouldBe nyJournalpostId
            forsendelseEtter.ferdigstiltTidspunkt shouldNotBe null
            forsendelseEtter.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"gjelderIdent\":\"${forsendelseEtter.gjelderIdent}\"," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Forsendelse notat\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelseEtter.saksnummer}\"]," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"NOTAT\"," +
                        "\"referanseId\":\"BIF_${forsendelseEtter.forsendelseId}\"," +
                        "\"journalførendeEnhet\":\"${forsendelseEtter.enhet}\"," +
                        "\"saksbehandlerIdent\":\"Z999444\"" +
                        "}"
            )

            stubUtils.Valider().hentDokumentKalt(forsendelseEtter.forsendelseIdMedPrefix, forsendelseEtter.dokumenter[0].dokumentreferanse)

        }
    }
}