package no.nav.bidrag.dokument.forsendelse.hendelse

import com.ninjasquad.springmockk.SpykBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.verify
import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.model.BIDRAG_DOKUMENT_FORSENDELSE_APP_ID
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.transport.dokument.DistribuerJournalpostRequest
import no.nav.bidrag.transport.dokument.DistribuerJournalpostResponse
import no.nav.bidrag.transport.dokument.DistribusjonInfoDto
import no.nav.bidrag.transport.dokument.JournalpostStatus
import no.nav.bidrag.transport.dokument.JournalpostType
import no.nav.bidrag.transport.dokument.OpprettDokumentDto
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OppdaterDistribusjonStatusTest : KafkaHendelseTestRunner() {

    @SpykBean
    private lateinit var journalpostHendelseProdusent: JournalpostKafkaHendelseProdusent

    @Autowired
    private lateinit var skedulering: ForsendelseSkedulering

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @LocalServerPort
    private val port = 0

    @Autowired
    lateinit var httpHeaderTestRestTemplate: HttpHeaderTestRestTemplate
    protected fun rootUri(): String {
        return "http://localhost:$port/api/forsendelse"
    }

    @BeforeEach
    fun resetSpys() {
        clearAllMocks()
    }

    private fun opprettForsendelseFerdigstiltIkkeDistribuert(): Forsendelse {
        return testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.FERDIGSTILT,
                arkivJournalpostId = (10000..20000).random().toString(),
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentMalId = "MAL1"
                    )
                )
            )
        )
    }

    private fun opprettForsendelseUnderProduksjon(): Forsendelse {
        return testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.UNDER_PRODUKSJON,
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentMalId = "MAL1"
                    )
                )
            )
        )
    }

    private fun opprettForsendelseDistribuert(): Forsendelse {
        return testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.DISTRIBUERT,
                distribusjonsTidspunkt = LocalDateTime.now(),
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
    fun `skal oppdatere distribusjon status`() {
        val forsendelse1 = opprettForsendelseFerdigstiltIkkeDistribuert()
        val forsendelse2 = opprettForsendelseFerdigstiltIkkeDistribuert()
        val forsendelseIkkeDistribuert = opprettForsendelseFerdigstiltIkkeDistribuert()
        opprettForsendelseDistribuert()
        opprettForsendelseDistribuert()
        opprettForsendelseDistribuert()
        opprettForsendelseUnderProduksjon()
        opprettForsendelseUnderProduksjon()
        opprettForsendelseUnderProduksjon()

        val distribuertDato = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)
        stubUtils.stubHentDistribusjonInfo(
            forsendelse1.journalpostIdFagarkiv,
            DistribusjonInfoDto(
                bestillingId = "bestillingid1",
                kanal = DistribusjonKanal.SDP.name,
                distribuertDato = distribuertDato,
                journalstatus = JournalpostStatus.DISTRIBUERT,
                distribuertAvIdent = "Z999999"
            )
        )
        stubUtils.stubHentDistribusjonInfo(
            forsendelse2.journalpostIdFagarkiv,
            DistribusjonInfoDto(
                bestillingId = "bestillingid3",
                kanal = DistribusjonKanal.SDP.name,
                distribuertDato = distribuertDato,
                journalstatus = JournalpostStatus.EKSPEDERT,
                distribuertAvIdent = "Z999999"
            )
        )
        stubUtils.stubHentDistribusjonInfo(
            forsendelseIkkeDistribuert.journalpostIdFagarkiv,
            DistribusjonInfoDto(
                kanal = DistribusjonKanal.SDP.name,
                journalstatus = JournalpostStatus.FERDIGSTILT
            )
        )

        transactionTemplate.executeWithoutResult {
            skedulering.oppdaterDistribusjonstatus()
        }

        stubUtils.Valider().hentDistribusjonInfoKalt(3)

        val forsendelse1Etter = testDataManager.hentForsendelse(forsendelse1.forsendelseId!!)
        val forsendelse2Etter = testDataManager.hentForsendelse(forsendelse2.forsendelseId!!)
        val forsendelseIkkeDistribuertEtter = testDataManager.hentForsendelse(forsendelseIkkeDistribuert.forsendelseId!!)
        assertSoftly {
            forsendelse1Etter!!.distribusjonKanal shouldBe DistribusjonKanal.SDP
            forsendelse1Etter.distribusjonBestillingsId shouldBe "bestillingid1"
            forsendelse1Etter.distribuertTidspunkt shouldBe distribuertDato
            forsendelse1Etter.status shouldBe ForsendelseStatus.DISTRIBUERT
            forsendelse1Etter.endretAvIdent shouldBe BIDRAG_DOKUMENT_FORSENDELSE_APP_ID

            forsendelse2Etter!!.status shouldBe ForsendelseStatus.DISTRIBUERT

            forsendelseIkkeDistribuertEtter!!.status shouldBe ForsendelseStatus.FERDIGSTILT

            verify(exactly = 2) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }

        val alleHendelser = readAllFromJournalpostTopic()
        val hendelse = alleHendelser.find { it.journalpostId == forsendelse1Etter!!.forsendelseIdMedPrefix }
        hendelse shouldNotBe null
        hendelse!!.status shouldBe JournalpostStatus.DISTRIBUERT
        hendelse.journalpostId shouldBe forsendelse1Etter!!.forsendelseIdMedPrefix
        hendelse.tema shouldBe forsendelse1Etter.tema.name
        hendelse.enhet shouldBe forsendelse1Etter.enhet
        hendelse.tittel shouldBe forsendelse1Etter.dokumenter.hoveddokument?.tittel
        hendelse.fnr shouldBe forsendelse1Etter.gjelderIdent
        hendelse.journalposttype shouldBe JournalpostType.UTGÅENDE.name
        hendelse.sakstilknytninger shouldContain forsendelse1Etter.saksnummer

        val hendelseLokalUtskrift = alleHendelser.find { it.journalpostId == forsendelse2Etter!!.forsendelseIdMedPrefix }
        hendelseLokalUtskrift!!.status shouldBe JournalpostStatus.DISTRIBUERT
    }

    @Test
    fun `skal oppdatere distribusjon for journalpost med status ekspedert og lokal utskrift`() {
        val forsendelse = opprettForsendelseFerdigstiltIkkeDistribuert()

        val distribuertDato = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)

        stubUtils.stubHentDistribusjonInfo(
            forsendelse.journalpostIdFagarkiv,
            DistribusjonInfoDto(
                bestillingId = "bestillingid2",
                kanal = DistribusjonKanal.LOKAL_UTSKRIFT.name,
                distribuertDato = distribuertDato,
                journalstatus = JournalpostStatus.EKSPEDERT,
                distribuertAvIdent = "Z999999"
            )
        )
        transactionTemplate.executeWithoutResult {
            skedulering.oppdaterDistribusjonstatus()
        }

        stubUtils.Valider().hentDistribusjonInfoKalt(1)

        val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)
        assertSoftly {
            forsendelseEtter!!.distribusjonKanal shouldBe DistribusjonKanal.LOKAL_UTSKRIFT
            forsendelseEtter.distribusjonBestillingsId shouldBe "bestillingid2"
            forsendelseEtter.distribuertTidspunkt shouldBe distribuertDato
            forsendelseEtter.status shouldBe ForsendelseStatus.DISTRIBUERT_LOKALT
            forsendelseEtter.endretAvIdent shouldBe BIDRAG_DOKUMENT_FORSENDELSE_APP_ID

            verify(exactly = 1) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }

        val alleHendelser = readAllFromJournalpostTopic()
        val hendelse = alleHendelser.find { it.journalpostId == forsendelse.forsendelseIdMedPrefix }
        hendelse shouldNotBe null
        hendelse!!.status shouldBe JournalpostStatus.DISTRIBUERT
        hendelse.journalpostId shouldBe forsendelseEtter!!.forsendelseIdMedPrefix
        hendelse.tema shouldBe forsendelseEtter.tema.name
        hendelse.enhet shouldBe forsendelseEtter.enhet
        hendelse.tittel shouldBe forsendelseEtter.dokumenter.hoveddokument?.tittel
        hendelse.fnr shouldBe forsendelseEtter.gjelderIdent
        hendelse.journalposttype shouldBe JournalpostType.UTGÅENDE.name
        hendelse.sakstilknytninger shouldContain forsendelseEtter.saksnummer
    }

    @Test
    fun `skal oppdatere distribusjon for journalpost med kanal INGEN_DISTRIBUSJON`() {
        val forsendelse = opprettForsendelseFerdigstiltIkkeDistribuert()
        testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.FERDIGSTILT,
                arkivJournalpostId = (10000..20000).random().toString(),
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
                kanal = DistribusjonKanal.INGEN_DISTRIBUSJON
            )
        )
        val distribuertDato = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)

        stubUtils.stubHentDistribusjonInfo(
            forsendelse.journalpostIdFagarkiv,
            DistribusjonInfoDto(
                bestillingId = null,
                kanal = DistribusjonKanal.INGEN_DISTRIBUSJON.name,
                distribuertDato = null,
                journalstatus = JournalpostStatus.FERDIGSTILT,
                distribuertAvIdent = "Z999999"
            )
        )
        transactionTemplate.executeWithoutResult {
            skedulering.oppdaterDistribusjonstatus()
        }

        stubUtils.Valider().hentDistribusjonInfoKalt(1)

        val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)
        assertSoftly {
            forsendelseEtter!!.distribusjonKanal shouldBe DistribusjonKanal.INGEN_DISTRIBUSJON
            forsendelseEtter.distribusjonBestillingsId shouldBe null
            forsendelseEtter.distribuertTidspunkt!! shouldHaveSameDayAs distribuertDato
            forsendelseEtter.status shouldBe ForsendelseStatus.FERDIGSTILT
            forsendelseEtter.endretAvIdent shouldBe BIDRAG_DOKUMENT_FORSENDELSE_APP_ID

            verify(exactly = 1) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }

        val alleHendelser = readAllFromJournalpostTopic()
        val hendelse = alleHendelser.find { it.journalpostId == forsendelse.forsendelseIdMedPrefix }
        hendelse shouldNotBe null
        hendelse!!.status shouldBe JournalpostStatus.DISTRIBUERT
        hendelse.journalpostId shouldBe forsendelseEtter!!.forsendelseIdMedPrefix
        hendelse.tema shouldBe forsendelseEtter.tema.name
        hendelse.enhet shouldBe forsendelseEtter.enhet
        hendelse.tittel shouldBe forsendelseEtter.dokumenter.hoveddokument?.tittel
        hendelse.fnr shouldBe forsendelseEtter.gjelderIdent
        hendelse.journalposttype shouldBe JournalpostType.UTGÅENDE.name
        hendelse.sakstilknytninger shouldContain forsendelseEtter.saksnummer
    }

    @Test
    fun `skal oppdatere distribusjon for journalpost med status ekspedert`() {
        val forsendelse = opprettForsendelseFerdigstiltIkkeDistribuert()

        val distribuertDato = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)

        stubUtils.stubHentDistribusjonInfo(
            forsendelse.journalpostIdFagarkiv,
            DistribusjonInfoDto(
                bestillingId = "bestillingid2",
                kanal = DistribusjonKanal.SDP.name,
                distribuertDato = distribuertDato,
                journalstatus = JournalpostStatus.EKSPEDERT,
                distribuertAvIdent = "Z999999"
            )
        )
        transactionTemplate.executeWithoutResult {
            skedulering.oppdaterDistribusjonstatus()
        }

        stubUtils.Valider().hentDistribusjonInfoKalt(1)

        val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)
        assertSoftly {
            forsendelseEtter!!.distribusjonKanal shouldBe DistribusjonKanal.SDP
            forsendelseEtter.distribusjonBestillingsId shouldBe "bestillingid2"
            forsendelseEtter.distribuertTidspunkt shouldBe distribuertDato
            forsendelseEtter.status shouldBe ForsendelseStatus.DISTRIBUERT
            forsendelseEtter.endretAvIdent shouldBe BIDRAG_DOKUMENT_FORSENDELSE_APP_ID

            verify(exactly = 1) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }

        val alleHendelser = readAllFromJournalpostTopic()
        val hendelse = alleHendelser.find { it.journalpostId == forsendelse.forsendelseIdMedPrefix }
        hendelse shouldNotBe null
        hendelse!!.status shouldBe JournalpostStatus.DISTRIBUERT
        hendelse.journalpostId shouldBe forsendelseEtter!!.forsendelseIdMedPrefix
        hendelse.tema shouldBe forsendelseEtter.tema.name
        hendelse.enhet shouldBe forsendelseEtter.enhet
        hendelse.tittel shouldBe forsendelseEtter.dokumenter.hoveddokument?.tittel
        hendelse.fnr shouldBe forsendelseEtter.gjelderIdent
        hendelse.journalposttype shouldBe JournalpostType.UTGÅENDE.name
        hendelse.sakstilknytninger shouldContain forsendelseEtter.saksnummer
    }

    protected fun utførDistribuerForsendelse(
        forsendelseId: String,
        forespørsel: DistribuerJournalpostRequest? = null,
        batchId: String? = null
    ): ResponseEntity<DistribuerJournalpostResponse> {
        return httpHeaderTestRestTemplate.postForEntity<DistribuerJournalpostResponse>(
            "${rootUri()}/journal/distribuer/$forsendelseId${batchId?.let { "?batchId=$it" }}",
            forespørsel?.let { HttpEntity(it) }
        )
    }

    @Test
    @Disabled("Skru på dette hvis hendelse skal også bli sendt etter online distribusjon")
    fun `skal distribuere forsendelse`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0),
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        tittel = "Tittel vedlegg",
                        dokumentMalId = "BI100",
                        rekkefølgeIndeks = 1
                    )
                )
            )
        )

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") }
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            verify(exactly = 1) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }

        val hendelse = readFromJournalpostTopic()
        hendelse shouldNotBe null
        hendelse!!.status shouldBe JournalpostStatus.DISTRIBUERT.name
        hendelse.journalpostId shouldBe oppdatertForsendelse.forsendelseIdMedPrefix
        hendelse.tema shouldBe oppdatertForsendelse.tema.name
        hendelse.enhet shouldBe oppdatertForsendelse.enhet
        hendelse.tittel shouldBe oppdatertForsendelse.dokumenter.hoveddokument?.tittel
        hendelse.fnr shouldBe oppdatertForsendelse.gjelderIdent
        hendelse.journalposttype shouldBe JournalpostType.UTGÅENDE.name
        hendelse.sakstilknytninger shouldContain oppdatertForsendelse.saksnummer
    }

    @Test
    @Disabled("Skru på dette hvis hendelse skal også bli sendt etter online distribusjon")
    fun `skal distribuere forsendelse lokalt og sende hendelse`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0),
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        tittel = "Tittel vedlegg",
                        dokumentMalId = "BI100",
                        rekkefølgeIndeks = 1
                    )
                )
            )
        )

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") }
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix, DistribuerJournalpostRequest(lokalUtskrift = true))

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe null
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT_LOKALT
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            verify(exactly = 1) { journalpostHendelseProdusent.publiserForsendelse(ofType(Forsendelse::class)) }
        }

        val hendelse = readFromJournalpostTopic()
        hendelse shouldNotBe null
        hendelse!!.status shouldBe JournalpostStatus.DISTRIBUERT.name
        hendelse.journalpostId shouldBe oppdatertForsendelse.forsendelseIdMedPrefix
        hendelse.tema shouldBe oppdatertForsendelse.tema.name
        hendelse.enhet shouldBe oppdatertForsendelse.enhet
        hendelse.tittel shouldBe oppdatertForsendelse.dokumenter.hoveddokument?.tittel
        hendelse.fnr shouldBe oppdatertForsendelse.gjelderIdent
        hendelse.journalposttype shouldBe JournalpostType.UTGÅENDE.name
        hendelse.sakstilknytninger shouldContain oppdatertForsendelse.saksnummer
    }
}
