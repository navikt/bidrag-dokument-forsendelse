package no.nav.bidrag.dokument.forsendelse.hendelse

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.TestContainerRunner
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.opprettReferanseId
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.service.FORSENDELSE_APP_ID
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_2
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class OppdaterStatusDokumenterSkeduleringTest : TestContainerRunner() {
    @Autowired
    private lateinit var skedulering: DokumentHendelseLytter

    @MockkBean
    lateinit var kafkaHendelseProdusent: DokumentKafkaHendelseProdusent

    @MockkBean
    lateinit var journalpostKafkaHendelseProdusent: JournalpostKafkaHendelseProdusent

    @BeforeEach
    fun setupMocks() {
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
        every { kafkaHendelseProdusent.publiser(any()) } returns Unit
        every { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) } returns Unit
        every { journalpostKafkaHendelseProdusent.publiser(any()) } returns Unit
    }

    @Test
    fun `Skal oppdatere status på dokumenter som er under redigering`() {
        val forsendelse1 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_2,
                                opprettetTidspunkt = LocalDateTime.now().minusHours(24),
                            ),
                        ),
                ),
            )

        val forsendelse2 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(24),
                            ),
                        ),
                ),
            )
        val forsendelse3 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 3",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL3",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(24),
                            ),
                        ),
                ),
            )

        val dokref1 = forsendelse1.dokumenter.hoveddokument!!.dokumentreferanse
        val dokref2 = forsendelse2.dokumenter.hoveddokument!!.dokumentreferanse
        val dokref3 = forsendelse3.dokumenter.hoveddokument!!.dokumentreferanse
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref1, true)
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref2, true)
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref3, true)
        skedulering.oppdaterStatusPaFerdigstilteDokumenter()

        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref1)
        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref2)
        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref3)
        assertSoftly {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse1.forsendelseId!!)
            val dokument = forsendelseEtter!!.dokumenter[0]
            dokument.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            dokument.ferdigstiltAvIdent!! shouldBe FORSENDELSE_APP_ID
        }

        assertSoftly {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse2.forsendelseId!!)
            val dokument = forsendelseEtter!!.dokumenter[0]
            dokument.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            dokument.ferdigstiltAvIdent!! shouldBe FORSENDELSE_APP_ID
        }

        assertSoftly {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse3.forsendelseId!!)
            val dokument = forsendelseEtter!!.dokumenter[0]
            dokument.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            dokument.ferdigstiltAvIdent!! shouldBe FORSENDELSE_APP_ID
        }

        verify(exactly = 3) { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) }
        verify {
            journalpostKafkaHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelse1.forsendelseId
                },
            )
            journalpostKafkaHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelse2.forsendelseId
                },
            )
            journalpostKafkaHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelse3.forsendelseId
                },
            )
        }
    }

    @Test
    fun `Skal ferdigstille notat som har status under redigering`() {
        val nyJournalpostId = "1331234412321"
        stubUtils.stubHentDokument()
        stubUtils.stubOpprettJournalpost(nyJournalpostId)
        val forsendelseNotat =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    erNotat = true,
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "Forsendelse notat",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = DOKUMENTMAL_NOTAT,
                                dokumentDato = LocalDateTime.parse("2022-01-05T01:02:03"),
                                opprettetTidspunkt = LocalDateTime.now().minusHours(24),
                            ),
                        ),
                ),
            )

        val forsendelse2 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(24),
                            ),
                        ),
                ),
            )

        val dokrefNotat = forsendelseNotat.dokumenter.hoveddokument!!.dokumentreferanse
        val dokref2 = forsendelse2.dokumenter.hoveddokument!!.dokumentreferanse
        stubUtils.stubSjekkErDokumentFerdigstilt(dokrefNotat, true)
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref2, true)
        skedulering.oppdaterStatusPaFerdigstilteDokumenter()

        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokrefNotat)
        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref2)
        assertSoftly("Skal ferdigstille notat") {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelseNotat.forsendelseId!!)
            val dokument = forsendelseEtter!!.dokumenter[0]
            forsendelseEtter.journalpostIdFagarkiv shouldBe nyJournalpostId
            dokument.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            dokument.ferdigstiltAvIdent!! shouldBe FORSENDELSE_APP_ID

            val referanseId = forsendelseEtter.opprettReferanseId()
            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                    "\"skalFerdigstilles\":true," +
                    "\"tittel\":\"Forsendelse notat\"," +
                    "\"gjelderIdent\":\"${forsendelseEtter.gjelderIdent}\"," +
                    "\"dokumenter\":[" +
                    "{\"tittel\":\"Forsendelse notat\",\"brevkode\":\"$DOKUMENTMAL_NOTAT\",\"dokumentmalId\":\"$DOKUMENTMAL_NOTAT\",\"dokumentreferanse\":\"${forsendelseEtter.dokumenter[0].dokumentreferanse}\"}]," +
                    "\"tilknyttSaker\":[\"${forsendelseEtter.saksnummer}\"]," +
                    "\"datoDokument\":\"2022-01-05T01:02:03\"," +
                    "\"tema\":\"BID\"," +
                    "\"journalposttype\":\"NOTAT\"," +
                    "\"referanseId\":\"$referanseId\"," +
                    "\"journalførendeEnhet\":\"${forsendelseEtter.enhet}\"," +
                    "\"saksbehandlerIdent\":\"Z999444\"" +
                    "}",
            )
        }

        assertSoftly {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse2.forsendelseId!!)
            val dokument = forsendelseEtter!!.dokumenter[0]
            dokument.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokument.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            dokument.ferdigstiltAvIdent!! shouldBe FORSENDELSE_APP_ID
        }

        verify(exactly = 1) { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) }
        verify {
            journalpostKafkaHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelse2.forsendelseId
                },
            )
        }
    }

    @Test
    fun `Skal ikke oppdatere dokument status til ferdigstilt hvis dokument ikke er ferdigstilt`() {
        val nyJournalpostId = "1331234412321"
        stubUtils.stubHentDokument()
        stubUtils.stubOpprettJournalpost(nyJournalpostId)

        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(24),
                            ),
                        ),
                ),
            )

        val dokref = forsendelse.dokumenter.hoveddokument!!.dokumentreferanse
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref, false)
        skedulering.oppdaterStatusPaFerdigstilteDokumenter()

        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref)
        assertSoftly {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)
            val dokument = forsendelseEtter!!.dokumenter[0]
            dokument.dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING
            dokument.ferdigstiltTidspunkt shouldBe null
            dokument.ferdigstiltAvIdent shouldBe null
        }

        verify(exactly = 0) { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) }
    }

    @Test
    fun `Skal ikke oppdatere dokument som er nyere enn 12 timer`() {
        val nyJournalpostId = "1331234412321"
        stubUtils.stubHentDokument()
        stubUtils.stubOpprettJournalpost(nyJournalpostId)

        val forsendelseFør12Timer =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(2),
                            ),
                        ),
                ),
            )

        val forsendelse2 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(13),
                            ),
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2 DOK 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(13),
                            ),
                        ),
                ),
            )

        val dokref = forsendelseFør12Timer.dokumenter.hoveddokument!!.dokumentreferanse
        val dokref2 = forsendelse2.dokumenter[0].dokumentreferanse
        val dokref3 = forsendelse2.dokumenter[1].dokumentreferanse
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref, true)
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref2, true)
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref3, true)
        skedulering.oppdaterStatusPaFerdigstilteDokumenter()

        stubUtils.Valider().stubSjekkErDokumentFerdigstiltIkkeKaltMed(dokref)
        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref2)
        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref3)
        assertSoftly("Forsendelse som er nyere enn 12 timer skal ikke sjekkes") {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelseFør12Timer.forsendelseId!!)
            val dokument = forsendelseEtter!!.dokumenter[0]
            dokument.dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING
            dokument.ferdigstiltTidspunkt shouldBe null
            dokument.ferdigstiltAvIdent shouldBe null
        }

        assertSoftly {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse2.forsendelseId!!)
            val dokument1 = forsendelseEtter!!.dokumenter[0]
            dokument1.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            val dokument2 = forsendelseEtter!!.dokumenter[1]
            dokument2.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
        }

        verify(exactly = 1) { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) }
        verify {
            journalpostKafkaHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelse2.forsendelseId
                },
            )
        }
    }

    @Test
    fun `Skal oppdatere dokumentstatus for alle lenket dokumenter`() {
        val nyJournalpostId = "1331234412321"
        stubUtils.stubHentDokument()
        stubUtils.stubOpprettJournalpost(nyJournalpostId)

        val forsendelseOriginal =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(13),
                            ),
                        ),
                ),
            )
        val dokrefOriginal = forsendelseOriginal.dokumenter[0].dokumentreferanse

        val forsendelseMedLenke =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = dokrefOriginal,
                                journalpostId = forsendelseOriginal.forsendelseId.toString(),
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2",
                                arkivsystem = DokumentArkivSystem.FORSENDELSE,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(13),
                            ),
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2 DOK 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(13),
                            ),
                        ),
                ),
            )
        val dokrefLenket = forsendelseMedLenke.dokumenter[0].dokumentreferanse
        val dokref3 = forsendelseMedLenke.dokumenter[1].dokumentreferanse

        stubUtils.stubSjekkErDokumentFerdigstilt(dokrefOriginal, true)
        stubUtils.stubSjekkErDokumentFerdigstilt(dokrefLenket, true)
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref3, true)
        skedulering.oppdaterStatusPaFerdigstilteDokumenter()

        stubUtils.Valider().stubSjekkErDokumentFerdigstiltIkkeKaltMed(dokrefLenket)
        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokrefOriginal)
        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref3)
        assertSoftly("Forsendelse original ferdigstilt") {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelseOriginal.forsendelseId!!)
            val dokument = forsendelseEtter!!.dokumenter[0]
            dokument.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
        }

        assertSoftly("Forsendelse lenket ferdigstitl") {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelseMedLenke.forsendelseId!!)
            val dokument1 = forsendelseEtter!!.dokumenter[0]
            dokument1.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            val dokument2 = forsendelseEtter!!.dokumenter[1]
            dokument2.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
        }

        verify(exactly = 2) { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) }
        verify {
            journalpostKafkaHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelseOriginal.forsendelseId
                },
            )
            journalpostKafkaHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelseMedLenke.forsendelseId
                },
            )
        }
    }

    @Test
    fun `Skal ikke sende hendelse hvis alle dokumenter ikke er ferdigstilt`() {
        val nyJournalpostId = "1331234412321"
        stubUtils.stubHentDokument()
        stubUtils.stubOpprettJournalpost(nyJournalpostId)

        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(13),
                            ),
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                tittel = "FORSENDELSE 2 DOK 2",
                                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                                dokumentMalId = "MAL2",
                                opprettetTidspunkt = LocalDateTime.now().minusHours(13),
                            ),
                        ),
                ),
            )
        val dokref1 = forsendelse.dokumenter[0].dokumentreferanse
        val dokref2 = forsendelse.dokumenter[1].dokumentreferanse

        stubUtils.stubSjekkErDokumentFerdigstilt(dokref1, true)
        stubUtils.stubSjekkErDokumentFerdigstilt(dokref2, false)
        skedulering.oppdaterStatusPaFerdigstilteDokumenter()

        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref1)
        stubUtils.Valider().stubSjekkErDokumentFerdigstiltKaltMed(dokref2)

        assertSoftly("Forsendelse ferdigstilt") {
            val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)
            val dokument1 = forsendelseEtter!!.dokumenter[0]
            dokument1.dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING
            val dokument2 = forsendelseEtter!!.dokumenter[1]
            dokument2.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
        }

        verify(exactly = 0) { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) }
    }
}
