package no.nav.bidrag.dokument.forsendelse.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENT_FIL
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class FysiskDokumentServiceTest {
    @MockkBean
    lateinit var forsendelseTjeneste: ForsendelseTjeneste

    @MockkBean
    lateinit var dokumentTjeneste: DokumentTjeneste

    @MockkBean
    lateinit var bidragDokumentConsumer: BidragDokumentConsumer

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean
    lateinit var dokumentStorageService: DokumentStorageService

    @MockkBean
    lateinit var bidragDokumentBestillingConsumer: BidragDokumentBestillingConsumer

    lateinit var fysiskDokumentService: FysiskDokumentService

    @BeforeEach
    fun init() {
        fysiskDokumentService =
            FysiskDokumentService(
                forsendelseTjeneste,
                dokumentTjeneste,
                bidragDokumentConsumer,
                bidragDokumentBestillingConsumer,
                tilgangskontrollService,
                dokumentStorageService,
            )
        every { forsendelseTjeneste.lagre(any()) } returns opprettForsendelse2()
        every { forsendelseTjeneste.medForsendelseId(any()) } returns opprettForsendelse2()
        every { bidragDokumentConsumer.hentDokument(any(), any()) } returns DOKUMENT_FIL.toByteArray()
        every { tilgangskontrollService.sjekkTilgangForsendelse(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangSak(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangPerson(any()) } returns Unit
        every { dokumentStorageService.hentFil(any()) } returns DOKUMENT_FIL.toByteArray()
    }

    @Test
    fun `skal hente dokument fra storage hvis status er KONTROLLERT`() {
        var forsendelse =
            opprettForsendelse2(
                dokumenter = emptyList(),
            ).copy(forsendelseId = 100L)
        val dokumentKontrollert =
            nyttDokument(
                journalpostId = "123213213",
                dokumentreferanseOriginal = "13213213123",
                dokumentStatus = DokumentStatus.KONTROLLERT,
                tittel = "Tittel dokument",
                dokumentMalId = "BI100",
                rekkefølgeIndeks = 1,
            ).copy(dokumentId = 2L, forsendelse = forsendelse)

        forsendelse = forsendelse.copy(dokumenter = listOf(dokumentKontrollert))

        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        val dokument = fysiskDokumentService.hentFysiskDokument(dokumentKontrollert)

        dokument shouldBe DOKUMENT_FIL.toByteArray()
        verify(exactly = 1) { dokumentStorageService.hentFil(dokumentKontrollert.filsti) }
    }

    @Test
    fun `skal hente dokument fra ekstern kilde hvis ikke status KONTROLLERT`() {
        val journalpostId = "123123213"
        val dokumentreferanse = "5353535"
        var forsendelse =
            opprettForsendelse2(
                dokumenter = emptyList(),
            ).copy(forsendelseId = 100L)
        val dokumentKontrollert =
            nyttDokument(
                journalpostId = journalpostId,
                dokumentreferanseOriginal = dokumentreferanse,
                dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                tittel = "Tittel dokument",
                dokumentMalId = "BI100",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                rekkefølgeIndeks = 1,
            ).copy(dokumentId = 2L, forsendelse = forsendelse)

        forsendelse = forsendelse.copy(dokumenter = listOf(dokumentKontrollert))

        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        val dokument = fysiskDokumentService.hentFysiskDokument(dokumentKontrollert)

        dokument shouldBe DOKUMENT_FIL.toByteArray()
        verify(exactly = 0) { dokumentStorageService.hentFil(any()) }
        verify(exactly = 1) { bidragDokumentConsumer.hentDokument("BID-$journalpostId", dokumentreferanse) }
    }

    @Test
    fun `skal hente original dokument fra ekstern kilde hvis arkivsystem er FORSENDELSE`() {
        val journalpostId = "123123213"
        val dokumentreferanse = "5353535"
        var forsendelse =
            opprettForsendelse2(
                dokumenter = emptyList(),
            ).copy(forsendelseId = 100L)
        val dokumentLenket =
            nyttDokument(
                journalpostId = journalpostId,
                dokumentreferanseOriginal = dokumentreferanse,
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "Tittel dokument",
                dokumentMalId = "BI100",
                arkivsystem = DokumentArkivSystem.FORSENDELSE,
                rekkefølgeIndeks = 1,
            ).copy(dokumentId = 2L, forsendelse = forsendelse)

        var forsendelseOriginal =
            opprettForsendelse2(
                dokumenter = emptyList(),
            ).copy(forsendelseId = 200L)
        val dokumentOriginal =
            nyttDokument(
                journalpostId = null,
                dokumentreferanseOriginal = null,
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "Tittel dokument",
                dokumentMalId = "BI100",
                arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
                rekkefølgeIndeks = 1,
            ).copy(dokumentId = 5L, forsendelse = forsendelseOriginal)

        forsendelse = forsendelse.copy(dokumenter = listOf(dokumentLenket))
        forsendelseOriginal = forsendelseOriginal.copy(dokumenter = listOf(dokumentOriginal))

        every { forsendelseTjeneste.medForsendelseId(eq(100L)) } returns forsendelse
        every { forsendelseTjeneste.medForsendelseId(eq(200L)) } returns forsendelseOriginal
        every { dokumentTjeneste.hentOriginalDokument(any()) } returns dokumentOriginal

        val dokument = fysiskDokumentService.hentFysiskDokument(dokumentLenket)

        dokument shouldBe DOKUMENT_FIL.toByteArray()
        verify(exactly = 0) { dokumentStorageService.hentFil(any()) }
        verify(
            exactly = 1,
        ) { bidragDokumentConsumer.hentDokument(forsendelseOriginal.forsendelseIdMedPrefix, dokumentOriginal.dokumentreferanse) }
    }
}
