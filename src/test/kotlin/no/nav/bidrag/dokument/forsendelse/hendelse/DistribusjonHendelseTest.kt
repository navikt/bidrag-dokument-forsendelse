package no.nav.bidrag.dokument.forsendelse.hendelse

import com.ninjasquad.springmockk.SpykBean
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.verify
import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.JournalpostStatus
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.Duration
import java.time.LocalDateTime

class DistribusjonHendelseTest : KafkaHendelseTestRunner() {

    @SpykBean
    private lateinit var journalpostHendelseProdusent: JournalpostKafkaHendelseProdusent

    @LocalServerPort
    private val port = 0

    @Autowired
    lateinit var httpHeaderTestRestTemplate: HttpHeaderTestRestTemplate
    protected fun rootUri(): String {
        return "http://localhost:$port/api/forsendelse"
    }

    protected fun utførDistribuerForsendelse(
        forsendelseId: String,
        forespørsel: DistribuerJournalpostRequest? = null,
        batchId: String? = null
    ): ResponseEntity<DistribuerJournalpostResponse> {
        return httpHeaderTestRestTemplate.exchange(
            "${rootUri()}/journal/distribuer/$forsendelseId${batchId?.let { "?batchId=$it" }}",
            HttpMethod.POST,
            forespørsel?.let { HttpEntity(it) },
            DistribuerJournalpostResponse::class.java
        )
    }

    @Test
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
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") })

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        await.atMost(Duration.ofSeconds(1)).untilAsserted {

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
        hendelse.sakstilknytninger!! shouldContain oppdatertForsendelse.saksnummer

    }

    @Test
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
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") })

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix, DistribuerJournalpostRequest(lokalUtskrift = true))

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        await.atMost(Duration.ofSeconds(1)).untilAsserted {

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
        hendelse.sakstilknytninger!! shouldContain oppdatertForsendelse.saksnummer

    }
}