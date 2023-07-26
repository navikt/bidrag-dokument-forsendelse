package no.nav.bidrag.dokument.forsendelse.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENT_FIL
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.NY_JOURNALPOSTID
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettAdresseDo
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
class FerdigstillForsendelseServiceTest {


    @MockkBean
    lateinit var forsendelseTjeneste: ForsendelseTjeneste

    @MockkBean
    lateinit var bidragDokumentConsumer: BidragDokumentConsumer

    @MockkBean
    lateinit var fysiskDokumentService: FysiskDokumentService

    @MockkBean
    lateinit var saksbehandlerInfoManager: SaksbehandlerInfoManager

    lateinit var ferdigstillForsendelseService: FerdigstillForsendelseService

    @BeforeEach
    fun init() {
        ferdigstillForsendelseService = FerdigstillForsendelseService(
            saksbehandlerInfoManager,
            forsendelseTjeneste,
            bidragDokumentConsumer,
            fysiskDokumentService
        )
        every { forsendelseTjeneste.lagre(any()) } returns opprettForsendelse2()
        every { fysiskDokumentService.hentFysiskDokument(any<Dokument>()) } returns DOKUMENT_FIL.toByteArray()
        every { saksbehandlerInfoManager.erApplikasjonBruker() } returns true
        every { bidragDokumentConsumer.opprettJournalpost(any()) } returns OpprettJournalpostResponse(NY_JOURNALPOSTID)
    }

    @Test
    fun `skal oprette forsendelse med referanseId`() {
        val forsendelse = opprettForsendelse2(
            tittel = null,
            erNotat = true,
            dokumenter = listOf(
                nyttDokument(
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    rekkefølgeIndeks = 0,
                    tittel = "Hoveddokument tittel"
                ).copy(dokumentId = 1L),
                nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1
                ).copy(dokumentId = 2L)
            )
        ).copy(forsendelseId = 123L)
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        ferdigstillForsendelseService.ferdigstillForsendelse(123213L, false)

        verify {
            bidragDokumentConsumer.opprettJournalpost(withArg {
                it.referanseId shouldBe "BIF_123"
            })
        }
    }

    @Test
    fun `skal oppdatere forsendelse detaljer etter ferdigstillelse`() {
        val forsendelse = opprettForsendelse2(
            tittel = "Forsendelse tittel",
            dokumenter = listOf(
                nyttDokument(
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    rekkefølgeIndeks = 0,
                    tittel = "Hoveddokument tittel"
                ).copy(dokumentId = 1L),
                nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1
                ).copy(dokumentId = 2L)
            )
        )
        every { bidragDokumentConsumer.opprettJournalpost(any()) } returns OpprettJournalpostResponse(
            NY_JOURNALPOSTID,
            dokumenter = forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") })
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        ferdigstillForsendelseService.ferdigstillForsendelse(123213L, true)

        verify {
            forsendelseTjeneste.lagre(withArg {
                it.journalpostIdFagarkiv shouldBe NY_JOURNALPOSTID
                it.status shouldBe ForsendelseStatus.FERDIGSTILT
                it.dokumenter.forEach { dok ->
                    dok.dokumentreferanseFagarkiv shouldBe "JOARK${dok.dokumentreferanse}"
                }
                it.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            })
        }
    }

    @Test
    fun `skal lagre tittel med beskjed ved lokal distribusjon`() {
        val forsendelse = opprettForsendelse2(
            tittel = "Forsendelse tittel",
            dokumenter = listOf(
                nyttDokument(
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    rekkefølgeIndeks = 0,
                    tittel = "Hoveddokument tittel"
                ).copy(dokumentId = 1L),
                nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1
                ).copy(dokumentId = 2L)
            )
        )
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        ferdigstillForsendelseService.ferdigstillForsendelse(123213L, true)

        verify {
            bidragDokumentConsumer.opprettJournalpost(withArg {
                it.tittel shouldBe "Forsendelse tittel (dokumentet er sendt per post med vedlegg)"
                it.dokumenter[0].tittel shouldBe "Hoveddokument tittel (dokumentet er sendt per post med vedlegg)"
            })
        }
    }

    @Test
    fun `skal lagre tittel hoveddokument tittel hvis forsendelse ikke har tittel for lokal distribusjon`() {
        val forsendelse = opprettForsendelse2(
            tittel = null,
            dokumenter = listOf(
                nyttDokument(
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    rekkefølgeIndeks = 0,
                    tittel = "Hoveddokument tittel"
                ).copy(dokumentId = 1L),
                nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1
                ).copy(dokumentId = 2L)
            )
        )
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        ferdigstillForsendelseService.ferdigstillForsendelse(123213L, true)

        verify {
            bidragDokumentConsumer.opprettJournalpost(withArg {
                it.tittel shouldBe "Hoveddokument tittel (dokumentet er sendt per post med vedlegg)"
                it.dokumenter[0].tittel shouldBe "Hoveddokument tittel (dokumentet er sendt per post med vedlegg)"
            })
        }
    }

    @Test
    fun `skal lagre tittel hoveddokument tittel hvis forsendelse ikke har tittel`() {
        val forsendelse = opprettForsendelse2(
            tittel = null,
            dokumenter = listOf(
                nyttDokument(
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    rekkefølgeIndeks = 0,
                    tittel = "Hoveddokument tittel"
                ).copy(dokumentId = 1L),
                nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1
                ).copy(dokumentId = 2L)
            )
        )
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        ferdigstillForsendelseService.ferdigstillForsendelse(123213L, false)

        verify {
            bidragDokumentConsumer.opprettJournalpost(withArg {
                it.tittel shouldBe "Hoveddokument tittel"
                it.dokumenter[0].tittel shouldBe "Hoveddokument tittel"
            })
        }
    }

    @Test
    fun `skal lagre mottaker for utgående forsendelse`() {
        val forsendelse = opprettForsendelse2(
            tittel = null,
            mottaker = Mottaker(ident = MOTTAKER_IDENT, navn = MOTTAKER_NAVN, adresse = opprettAdresseDo()),
            dokumenter = listOf(
                nyttDokument(
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    rekkefølgeIndeks = 0,
                    tittel = "Hoveddokument tittel"
                ).copy(dokumentId = 1L),
                nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1
                ).copy(dokumentId = 2L)
            )
        )
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        ferdigstillForsendelseService.ferdigstillForsendelse(123213L, false)

        verify {
            bidragDokumentConsumer.opprettJournalpost(withArg {
                it.avsenderMottaker shouldNotBe null
                it.avsenderMottaker!!.ident shouldBe MOTTAKER_IDENT
                it.avsenderMottaker!!.navn shouldBe MOTTAKER_NAVN
            })
        }
    }

    @Test
    fun `skal ikke lagre mottaker hvis notat`() {
        val forsendelse = opprettForsendelse2(
            tittel = null,
            erNotat = true,
            dokumenter = listOf(
                nyttDokument(
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    rekkefølgeIndeks = 0,
                    tittel = "Hoveddokument tittel"
                ).copy(dokumentId = 1L),
                nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1
                ).copy(dokumentId = 2L)
            )
        )
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        ferdigstillForsendelseService.ferdigstillForsendelse(123213L, false)

        verify {
            bidragDokumentConsumer.opprettJournalpost(withArg {
                it.avsenderMottaker shouldBe null
            })
        }
    }

    @Test
    fun `skal lagre dokumenter`() {
        val forsendelse = opprettForsendelse2(
            tittel = null,
            dokumenter = listOf(
                nyttDokument(
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    rekkefølgeIndeks = 0,
                    tittel = "Hoveddokument tittel"
                ).copy(dokumentId = 1L),
                nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1
                ).copy(dokumentId = 2L)
            )
        )
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        ferdigstillForsendelseService.ferdigstillForsendelse(123213L, false)

        verify {
            bidragDokumentConsumer.opprettJournalpost(withArg {
                it.dokumenter.forEach { dok ->
                    dok.fysiskDokument shouldBe DOKUMENT_FIL.toByteArray()
                }
            })
        }
    }
}