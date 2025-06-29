package no.nav.bidrag.dokument.forsendelse.hendelse

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveHour
import io.kotest.matchers.shouldBe
import io.mockk.Ordering
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.TestContainerRunner
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.DokumentMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_2
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_3
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.dokument.DokumentHendelseType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class BestillFeiledeDokumentereSkeduleringTest : TestContainerRunner() {
    @Autowired
    private lateinit var skedulering: DokumentSkedulering

    @MockkBean
    lateinit var kafkaHendelseProdusent: DokumentKafkaHendelseProdusent

    @BeforeEach
    fun setupMocks() {
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
        every { kafkaHendelseProdusent.publiser(any()) } returns Unit
    }

    @Test
    fun `Skal bestille feilede dokumenter på nytt`() {
        val forsendelse1 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_2,
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
                                dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                dokumentMalId = "MAL2",
                            ),
                        ),
                ),
            )
        testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter =
                    listOf(
                        nyttDokument(
                            dokumentreferanseOriginal = null,
                            journalpostId = null,
                            dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                            tittel = "FORSENDELSE 1",
                            arkivsystem = DokumentArkivSystem.UKJENT,
                            dokumentMalId = "MAL3",
                        ),
                    ),
            ),
        )

        skedulering.bestillFeiledeDokumenterPåNytt()

        stubUtils.Valider().bestillDokumentKaltMed(
            DOKUMENTMAL_UTGÅENDE_2,
            "\"saksbehandler\":{\"ident\":\"Z999444\",\"navn\":null}",
            "\"dokumentreferanse\":\"${forsendelse1.dokumenter.hoveddokument!!.dokumentreferanse}\"",
        )
        stubUtils.Valider().bestillDokumentKaltMed(
            "MAL2",
            "\"saksbehandler\":{\"ident\":\"Z999444\",\"navn\":null}",
            "\"dokumentreferanse\":\"${forsendelse2.dokumenter.hoveddokument!!.dokumentreferanse}\"",
        )
        stubUtils.Valider().bestillDokumentIkkeKalt("MAL3")
    }

    @Test
    fun `Skal ignorere dokumenter hvor forsendelse ikke har status UNDER_PRODUKSJON`() {
        testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.SLETTET,
                dokumenter =
                    listOf(
                        nyttDokument(
                            dokumentreferanseOriginal = null,
                            journalpostId = null,
                            dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                            tittel = "FORSENDELSE 1",
                            arkivsystem = DokumentArkivSystem.UKJENT,
                            dokumentMalId = DOKUMENTMAL_UTGÅENDE_2,
                        ),
                    ),
            ),
        )
        testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.SLETTET,
                dokumenter =
                    listOf(
                        nyttDokument(
                            dokumentreferanseOriginal = null,
                            journalpostId = null,
                            dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                            tittel = "FORSENDELSE 1",
                            arkivsystem = DokumentArkivSystem.UKJENT,
                            dokumentMalId = "MAL2",
                        ),
                    ),
            ),
        )

        skedulering.bestillFeiledeDokumenterPåNytt()

        stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_2)
        stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_3)
    }

    @Test
    fun `Skal bestille dokumenter med status under produksjon på nytt hvis de er eldre enn 10 min og ikke bestillt mer enn 10 ganger`() {
        val forsendelse1 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_2,
                            ).copy(opprettetTidspunkt = LocalDateTime.now()),
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
                                dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES,
                                metadata =
                                    run {
                                        val metadata = DokumentMetadataDo()
                                        metadata.lagreBestiltTidspunkt(LocalDateTime.now().minusMinutes(10))
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata
                                    },
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
                                dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                metadata =
                                    run {
                                        val metadata = DokumentMetadataDo()
                                        metadata.lagreBestiltTidspunkt(LocalDateTime.now().minusHours(2))
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata
                                    },
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2,
                            ),
                        ),
                ),
            )

        val forsendelse4Bestilt10Ganger =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                metadata =
                                    run {
                                        val metadata = DokumentMetadataDo()
                                        metadata.lagreBestiltTidspunkt(LocalDateTime.now().minusHours(2))
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata.inkrementerBestiltAntallGanger()
                                        metadata
                                    },
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2,
                            ),
                        ),
                ),
            )

        skedulering.bestillDokumenterUnderProduksjonPåNytt()

        val dokument2 = testDataManager.hentForsendelse(forsendelse2.forsendelseId!!)!!.dokumenter.hoveddokument!!
        val dokument3 = testDataManager.hentForsendelse(forsendelse3.forsendelseId!!)!!.dokumenter.hoveddokument!!
        val dokument4 = testDataManager.hentForsendelse(forsendelse4Bestilt10Ganger.forsendelseId!!)!!.dokumenter.hoveddokument!!
        assertSoftly {
            dokument4.metadata.hentDokumentBestiltAntallGanger() shouldBe 10
            dokument4.metadata.hentBestiltTidspunkt()!! shouldHaveHour
                LocalDateTime
                    .now()
                    .minusHours(2)
                    .hour
            dokument2.metadata.hentDokumentBestiltAntallGanger() shouldBe 2
            dokument3.metadata.hentDokumentBestiltAntallGanger() shouldBe 2
            dokument2.metadata.hentBestiltTidspunkt()!! shouldHaveHour LocalDateTime.now().hour
            dokument3.metadata.hentBestiltTidspunkt()!! shouldHaveHour LocalDateTime.now().hour
        }

        verify(exactly = 2) {
            kafkaHendelseProdusent.publiser(any())
        }
        verify(ordering = Ordering.SEQUENCE) {
            kafkaHendelseProdusent.publiser(
                withArg {
                    it.forsendelseId shouldBe forsendelse2.forsendelseId.toString()
                    it.hendelseType shouldBe DokumentHendelseType.BESTILLING
                    it.dokumentreferanse shouldBe forsendelse2.dokumenter.hoveddokument!!.dokumentreferanse
                },
            )
            kafkaHendelseProdusent.publiser(
                withArg {
                    it.forsendelseId shouldBe forsendelse3.forsendelseId.toString()
                    it.hendelseType shouldBe DokumentHendelseType.BESTILLING
                    it.dokumentreferanse shouldBe forsendelse3.dokumenter.hoveddokument!!.dokumentreferanse
                },
            )
        }
        stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2)
        stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES)
        stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_2)
    }

    @Test
    fun `Skal ikke bestille dokumenter med status under produksjon hvis de mangler bestilling metadata`() {
        val forsendelse1 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_2,
                            ).copy(opprettetTidspunkt = LocalDateTime.now().minusHours(1)),
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
                                dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES,
                                metadata = DokumentMetadataDo(),
                            ),
                        ),
                ),
            )

        skedulering.bestillDokumenterUnderProduksjonPåNytt()

        verify(exactly = 0) {
            kafkaHendelseProdusent.publiser(any())
        }
        stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES)
        stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_2)
    }

    @Test
    fun `Skal bestille dokument og ferdigstille hvis det er satt`() {
        val forsendelse1 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    behandlingInfo =
                        BehandlingInfo(
                            vedtakType = Vedtakstype.ALDERSJUSTERING,
                            vedtakId = "213",
                        ),
                    dokumenter =
                        listOf(
                            nyttDokument(
                                dokumentreferanseOriginal = null,
                                journalpostId = null,
                                dokumentStatus = DokumentStatus.IKKE_BESTILT,
                                tittel = "FORSENDELSE 1",
                                arkivsystem = DokumentArkivSystem.UKJENT,
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE_2,
                            ).copy(ferdigstill = true),
                        ),
                ),
            )

        skedulering.bestill(forsendelse1.dokumenter)

        stubUtils.Valider().bestillDokumentKaltMed(
            DOKUMENTMAL_UTGÅENDE_2,
            "\"erBatchBrev\":true",
            "\"saksbehandler\":{\"ident\":\"Z999444\",\"navn\":null}",
            "\"dokumentreferanse\":\"${forsendelse1.dokumenter.hoveddokument!!.dokumentreferanse}\"",
        )

        stubUtils.Valider().bestillDokumentIkkeKalt("MAL3")
    }
}
