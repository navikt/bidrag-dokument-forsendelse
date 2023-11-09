package no.nav.bidrag.dokument.forsendelse.hendelse

import com.ninjasquad.springmockk.SpykBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.opprettReferanseId
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.FORSENDELSE_APP_ID
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utils.opprettHendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.transport.dokument.DokumentStatusDto
import no.nav.bidrag.transport.dokument.JournalpostStatus
import no.nav.bidrag.transport.dokument.JournalpostType
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

    @Test
    fun `Skal oppdatere status på dokument til UNDER_REDIGERING og lagre produsert tidspunkt ved mottatt hendelse`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.UKJENT
                    )
                )
            )
        )
        val dokument = forsendelse.dokumenter[0]
        val hendelse = opprettHendelse(dokument.dokumentreferanse, status = DokumentStatusDto.UNDER_REDIGERING)
        sendMeldingTilDokumentHendelse(hendelse)

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
            val dokumentEtter = forsendelseEtter.dokumenter[0]
            dokumentEtter.dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING
            dokumentEtter.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
            dokumentEtter.metadata.hentProdusertTidspunkt() shouldNotBe null
            dokumentEtter.metadata.hentProdusertTidspunkt()!! shouldHaveSameDayAs LocalDateTime.now()
            verify(exactly = 0) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }
    }

    @Test
    fun `Skal oppdatere status på alle dokumenter til FERDIGSTILT ved mottatt hendelse`() {
        val forsendelse1 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.UKJENT
                    )
                )
            )
        )

        val dokumentreferanse = forsendelse1.dokumenter[0].dokumentreferanse
        val forsendelseMedLenketDokument = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = forsendelse1.forsendelseId.toString(),
                        dokumentreferanseOriginal = dokumentreferanse,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "FORSENDELSE 2",
                        arkivsystem = DokumentArkivSystem.FORSENDELSE
                    )
                )
            )
        )
        val hendelse = opprettHendelse(dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelse)

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            assertSoftly("Valider dokument for forsendelse 1") {
                val forsendelseEtter = testDataManager.hentForsendelse(forsendelse1.forsendelseId!!)!!
                val dokumentEtter = forsendelseEtter.dokumenter[0]
                dokumentEtter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
                dokumentEtter.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                dokumentEtter.ferdigstiltTidspunkt shouldNotBe null
                dokumentEtter.ferdigstiltAvIdent shouldBe FORSENDELSE_APP_ID
                dokumentEtter.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
                dokumentEtter.metadata.hentProdusertTidspunkt() shouldBe null // Skal ikke sette produsert tidspunkt hvis status går fra under redigering -> ferdigstilt
            }

            assertSoftly("Valider dokument for forsendelse 2 med lenket dokument") {
                val forsendelseEtter = testDataManager.hentForsendelse(forsendelseMedLenketDokument.forsendelseId!!)!!
                val dokumentEtter = forsendelseEtter.dokumenter[0]
                dokumentEtter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
                dokumentEtter.arkivsystem shouldBe DokumentArkivSystem.FORSENDELSE
                dokumentEtter.ferdigstiltTidspunkt shouldNotBe null
                dokumentEtter.ferdigstiltAvIdent shouldBe FORSENDELSE_APP_ID
                dokumentEtter.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
                dokumentEtter.metadata.hentProdusertTidspunkt() shouldBe null // Skal ikke sette produsert tidspunkt hvis status går fra under redigering -> ferdigstilt
            }

            verify(exactly = 2) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }
    }

    @Test
    fun `Skal oppdatere arkivsystem på dokumenter ved mottatt hendelse`() {
        val forsendelse1 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.UKJENT
                    )
                )
            )
        )

        val dokumentreferanse = forsendelse1.dokumenter[0].dokumentreferanse
        val forsendelse2 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = forsendelse1.forsendelseId.toString(),
                        dokumentreferanseOriginal = dokumentreferanse,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "FORSENDELSE 2",
                        arkivsystem = DokumentArkivSystem.FORSENDELSE
                    )
                )
            )
        )
        val hendelse = opprettHendelse(dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelse)

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse1Etter = testDataManager.hentForsendelse(forsendelse1.forsendelseId!!)!!
            val forsendelse2Etter = testDataManager.hentForsendelse(forsendelse2.forsendelseId!!)!!
            val dokument1Etter = forsendelse1Etter.dokumenter[0]
            val dokument2Etter = forsendelse2Etter.dokumenter[0]
            dokument1Etter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument2Etter.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument1Etter.ferdigstiltTidspunkt shouldNotBe null
            dokument1Etter.ferdigstiltAvIdent shouldBe FORSENDELSE_APP_ID
            dokument1Etter.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
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
        hendelse!!.status shouldBe JournalpostStatus.KLAR_FOR_DISTRIBUSJON
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
        val forsendelseNotat = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                erNotat = true,
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "Forsendelse notat",
                        arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                        dokumentDato = LocalDateTime.parse("2022-01-05T01:02:03")
                    )
                )
            )
        )

        val hendelse = opprettHendelse(forsendelseNotat.dokumenter[0].dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT)
        sendMeldingTilDokumentHendelse(hendelse)

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelseNotat.forsendelseId!!)!!

            forsendelseEtter.status shouldBe ForsendelseStatus.FERDIGSTILT
            forsendelseEtter.journalpostIdFagarkiv shouldBe nyJournalpostId
            forsendelseEtter.ferdigstiltTidspunkt shouldNotBe null
            forsendelseEtter.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            val referanseId = forsendelseEtter.opprettReferanseId()
            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                    "\"skalFerdigstilles\":true," +
                    "\"tittel\":\"Forsendelse notat\"," +
                    "\"gjelderIdent\":\"${forsendelseEtter.gjelderIdent}\"," +
                    "\"dokumenter\":[" +
                    "{\"tittel\":\"Forsendelse notat\",\"brevkode\":\"BI091\",\"dokumentmalId\":\"BI091\",\"dokumentreferanse\":\"${forsendelseEtter.dokumenter[0].dokumentreferanse}\"}]," +
                    "\"tilknyttSaker\":[\"${forsendelseEtter.saksnummer}\"]," +
                    "\"datoDokument\":\"2022-01-05T01:02:03\"," +
                    "\"tema\":\"BID\"," +
                    "\"journalposttype\":\"NOTAT\"," +
                    "\"referanseId\":\"$referanseId\"," +
                    "\"journalførendeEnhet\":\"${forsendelseEtter.enhet}\"," +
                    "\"saksbehandlerIdent\":\"Z999444\"" +
                    "}"
            )
        }
    }
}
