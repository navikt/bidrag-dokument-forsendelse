package no.nav.bidrag.dokument.forsendelse.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockkStatic
import no.nav.bidrag.commons.service.hentNavSkjemaKodeverk
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterEttersendelseDokumentRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterEttersendingsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettEttersendingsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.SlettEttersendingsoppgave
import no.nav.bidrag.dokument.forsendelse.api.dto.SlettEttersendingsoppgaveVedleggRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.InnsendingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentSoknadDto
import no.nav.bidrag.dokument.forsendelse.consumer.dto.VedleggDto
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Ettersendingsoppgave
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.EttersendingsoppgaveVedlegg
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BP
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.transport.dokument.AktorDto
import no.nav.bidrag.transport.dokument.DokumentType
import no.nav.bidrag.transport.dokument.JournalpostDto
import no.nav.bidrag.transport.dokument.KodeDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.client.HttpClientErrorException
import java.time.OffsetDateTime

@ExtendWith(SpringExtension::class)
class EttersendingsOppgaveServiceTest {
    @MockkBean
    lateinit var forsendelseTjeneste: ForsendelseTjeneste

    @MockkBean
    lateinit var bidragDokumentConsumer: BidragDokumentConsumer

    @MockkBean
    lateinit var innsendingConsumer: InnsendingConsumer

    lateinit var ettersendingsOppgaveService: EttersendingsOppgaveService
    val brevkode = "NAV 12-34.56"
    val brevkode2 = "NAV 12-34.57"

    @BeforeEach
    fun `set up`() {
        mockkStatic(::hentNavSkjemaKodeverk)
        every { hentNavSkjemaKodeverk() } returns
            mapOf(
                brevkode to "Søknad om bidrag",
                brevkode2 to "Søknad om forskudd",
            )
        ettersendingsOppgaveService = EttersendingsOppgaveService(bidragDokumentConsumer, forsendelseTjeneste, innsendingConsumer)
        every { innsendingConsumer.hentEttersendingsoppgave(any()) } returns
            listOf(
                DokumentSoknadDto(
                    id = 1,
                    brukerId = "12345678901",
                    skjemanr = "NAV 12-34.56",
                    tittel = "Søknad om bidrag",
                    opprettetDato = OffsetDateTime.now(),
                    vedleggsListe =
                        listOf(
                            VedleggDto(
                                id = 1,
                                tittel = "Kvittering fra barnehage",
                                opprettetdato = OffsetDateTime.now(),
                            ),
                        ),
                ),
            )
    }

    @Test
    fun `skal hente ettersendingsoppgaver for bruker`() {
        val gjelderIdent = "12345678901"
        val gjelderIdent2 = "1234561232178901"
        val brevkode = "NAV 12-34.56"
        val brevkode2 = "NAV 12-34.57"
        every { bidragDokumentConsumer.hentJournal(any()) } returns
            listOf(
                JournalpostDto(
                    journalpostId = "123456789",
                    gjelderAktor = AktorDto(ident = gjelderIdent),
                    dokumentType = DokumentType.INNGÅENDE,
                    brevkode = KodeDto(kode = brevkode),
                ),
                JournalpostDto(
                    journalpostId = "123453336789",
                    gjelderAktor = AktorDto(ident = gjelderIdent),
                    dokumentType = DokumentType.INNGÅENDE,
                    brevkode = KodeDto(kode = brevkode2),
                ),
                JournalpostDto(
                    journalpostId = "123453333136789",
                    gjelderAktor = AktorDto(ident = gjelderIdent2),
                    dokumentType = DokumentType.INNGÅENDE,
                    brevkode = KodeDto(kode = brevkode2),
                ),
                JournalpostDto(
                    journalpostId = "3333",
                    gjelderAktor = AktorDto(ident = gjelderIdent),
                    dokumentType = DokumentType.UTGÅENDE,
                    brevkode = KodeDto(kode = brevkode),
                ),
            )
        every { innsendingConsumer.hentEttersendingsoppgave(any()) } returns
            listOf(
                DokumentSoknadDto(
                    id = 1,
                    brukerId = gjelderIdent,
                    skjemanr = brevkode,
                    tittel = "Søknad om bidrag",
                    opprettetDato = OffsetDateTime.now(),
                    vedleggsListe =
                        listOf(
                            VedleggDto(
                                id = 1,
                                tittel = "Kvittering fra barnehage",
                                opprettetdato = OffsetDateTime.now(),
                            ),
                        ),
                ),
                DokumentSoknadDto(
                    id = 1,
                    brukerId = gjelderIdent,
                    skjemanr = brevkode2,
                    tittel = "Søknad om forskudd",
                    opprettetDato = OffsetDateTime.now(),
                    vedleggsListe =
                        listOf(
                            VedleggDto(
                                id = 1,
                                tittel = "Kvittering fra barnehage",
                                opprettetdato = OffsetDateTime.now(),
                            ),
                        ),
                ),
                DokumentSoknadDto(
                    id = 1,
                    brukerId = gjelderIdent2,
                    skjemanr = brevkode2,
                    tittel = "Søknad om bidrag",
                    opprettetDato = OffsetDateTime.now(),
                    vedleggsListe =
                        listOf(
                            VedleggDto(
                                id = 1,
                                tittel = "Kvittering fra barnehage",
                                opprettetdato = OffsetDateTime.now(),
                            ),
                        ),
                ),
            )
        val forsendelse = opprettForsendelse2().copy(gjelderIdent = gjelderIdent)
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse
        val ettersendingsoppgaver = ettersendingsOppgaveService.hentEksisterendeEttersendingsoppgaverForBruker(1)
        assertSoftly(ettersendingsoppgaver) {
            shouldHaveSize(2)
            shouldHaveKey(brevkode)
            shouldHaveKey(brevkode2)
            it[brevkode]!!.first().tittel shouldBe "Søknad om bidrag"
            it[brevkode]!![1].tittel shouldBe "Søknad om forskudd"
        }
    }

    @Test
    fun `skal oppdatere ettersendingsoppgave`() {
        val forsendelse = opprettForsendelse2(mottaker = Mottaker(ident = GJELDER_IDENT))
        val ettersendingsoppgave =
            Ettersendingsoppgave(
                forsendelse = forsendelse,
                tittel = "Tittel",
                innsendingsfristDager = 22,
                ettersendelseForJournalpostId = "123",
                skjemaId = "NAV 12-34.56",
            )
        forsendelse.ettersendingsoppgave = ettersendingsoppgave
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        val request =
            OppdaterEttersendingsoppgaveRequest(
                forsendelseId = 1L,
                tittel = "Oppdatert tittel",
                oppdaterDokument = null,
                innsendingsfristDager = 10,
                ettersendelseForJournalpostId = "333",
                skjemaId = "NAV 12-34.5623",
            )
        ettersendingsOppgaveService.oppdaterEttersendingsoppgave(request)

        forsendelse.ettersendingsoppgave!!.tittel shouldBe "Oppdatert tittel"
        forsendelse.ettersendingsoppgave!!.innsendingsfristDager shouldBe 10
        forsendelse.ettersendingsoppgave!!.ettersendelseForJournalpostId shouldBe "333"
        forsendelse.ettersendingsoppgave!!.skjemaId shouldBe "NAV 12-34.5623"
        forsendelse.ettersendingsoppgave!!.vedleggsliste.shouldHaveSize(0)
    }

    @Test
    fun `skal opprette ettersendingsoppgave vedlegg`() {
        val forsendelse = opprettForsendelse2(mottaker = Mottaker(ident = GJELDER_IDENT))
        val ettersendingsoppgave =
            Ettersendingsoppgave(
                forsendelse = forsendelse,
                tittel = "Tittel",
                innsendingsfristDager = 22,
                ettersendelseForJournalpostId = "123",
                skjemaId = "NAV 12-34.56",
            )
        forsendelse.ettersendingsoppgave = ettersendingsoppgave
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        val request =
            OppdaterEttersendingsoppgaveRequest(
                forsendelseId = 1L,
                tittel = "Oppdatert tittel",
                oppdaterDokument =
                    OppdaterEttersendelseDokumentRequest(
                        tittel = "Nytt vedlegg",
                        skjemaId = "Q5",
                    ),
            )
        ettersendingsOppgaveService.oppdaterEttersendingsoppgave(request)

        forsendelse.ettersendingsoppgave!!.tittel shouldBe "Oppdatert tittel"
        forsendelse.ettersendingsoppgave!!.innsendingsfristDager shouldBe 22
        forsendelse.ettersendingsoppgave!!.ettersendelseForJournalpostId shouldBe "123"
        forsendelse.ettersendingsoppgave!!.skjemaId shouldBe "NAV 12-34.56"
        forsendelse.ettersendingsoppgave!!.vedleggsliste.shouldHaveSize(1)
        val vedlegg = forsendelse.ettersendingsoppgave!!.vedleggsliste.first()
        assertSoftly(vedlegg) {
            id = 0
            tittel shouldBe "Nytt vedlegg"
            skjemaId shouldBe "Q5"
        }
    }

    @Test
    fun `skal oppdatere ettersendingsoppgave vedlegg`() {
        val forsendelse = opprettForsendelse2(mottaker = Mottaker(ident = GJELDER_IDENT))
        val ettersendingsoppgave =
            Ettersendingsoppgave(
                forsendelse = forsendelse,
                tittel = "Tittel",
                innsendingsfristDager = 22,
                ettersendelseForJournalpostId = "123",
                skjemaId = "NAV 12-34.56",
            )
        val vedlegg = EttersendingsoppgaveVedlegg(ettersendingsoppgave = ettersendingsoppgave, tittel = "Vedlegg", skjemaId = "Q5", id = 0)
        ettersendingsoppgave.vedleggsliste.add(vedlegg)
        forsendelse.ettersendingsoppgave = ettersendingsoppgave
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        val request =
            OppdaterEttersendingsoppgaveRequest(
                forsendelseId = 1L,
                tittel = "Oppdatert tittel",
                oppdaterDokument =
                    OppdaterEttersendelseDokumentRequest(
                        id = 0,
                        tittel = "Oppdatert vedlegg",
                        skjemaId = "A4",
                    ),
            )
        ettersendingsOppgaveService.oppdaterEttersendingsoppgave(request)

        forsendelse.ettersendingsoppgave!!.tittel shouldBe "Oppdatert tittel"
        forsendelse.ettersendingsoppgave!!.innsendingsfristDager shouldBe 22
        forsendelse.ettersendingsoppgave!!.ettersendelseForJournalpostId shouldBe "123"
        forsendelse.ettersendingsoppgave!!.skjemaId shouldBe "NAV 12-34.56"
        forsendelse.ettersendingsoppgave!!.vedleggsliste.shouldHaveSize(1)
        val oppdatertVedlegg = forsendelse.ettersendingsoppgave!!.vedleggsliste.first()
        assertSoftly(oppdatertVedlegg) {
            id = 0
            tittel shouldBe "Oppdatert vedlegg"
            skjemaId shouldBe "A4"
        }
    }

    @Test
    fun `skal slette ettersendingsoppgave vedlegg`() {
        val forsendelse = opprettForsendelse2()
        val ettersendingsoppgave =
            Ettersendingsoppgave(
                forsendelse = forsendelse,
                tittel = "Tittel",
                innsendingsfristDager = 22,
                ettersendelseForJournalpostId = "123",
                skjemaId = "NAV 12-34.56",
            )
        val vedlegg = EttersendingsoppgaveVedlegg(ettersendingsoppgave = ettersendingsoppgave, tittel = "Vedlegg", skjemaId = "Q5", id = 0)
        val vedlegg2 = EttersendingsoppgaveVedlegg(ettersendingsoppgave = ettersendingsoppgave, tittel = "Vedlegg 2", skjemaId = "Q9", id = 1)
        ettersendingsoppgave.vedleggsliste.add(vedlegg)
        ettersendingsoppgave.vedleggsliste.add(vedlegg2)
        forsendelse.ettersendingsoppgave = ettersendingsoppgave
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        ettersendingsOppgaveService.slettEttersendingsoppgaveVedlegg(SlettEttersendingsoppgaveVedleggRequest(1, 0))

        forsendelse.ettersendingsoppgave!!.tittel shouldBe "Tittel"
        forsendelse.ettersendingsoppgave!!.innsendingsfristDager shouldBe 22
        forsendelse.ettersendingsoppgave!!.ettersendelseForJournalpostId shouldBe "123"
        forsendelse.ettersendingsoppgave!!.skjemaId shouldBe "NAV 12-34.56"
        forsendelse.ettersendingsoppgave!!.vedleggsliste.shouldHaveSize(1)
        val oppdatertVedlegg = forsendelse.ettersendingsoppgave!!.vedleggsliste.first()
        assertSoftly(oppdatertVedlegg) {
            id = 1
            tittel shouldBe "Vedlegg 2"
            skjemaId shouldBe "Q9"
        }
    }

    @Test
    fun `skal slette ettersendingsoppgave`() {
        val forsendelse = opprettForsendelse2()
        val ettersendingsoppgave =
            Ettersendingsoppgave(
                forsendelse = forsendelse,
                tittel = "Tittel",
                innsendingsfristDager = 22,
                ettersendelseForJournalpostId = "123",
                skjemaId = "NAV 12-34.56",
            )
        val vedlegg = EttersendingsoppgaveVedlegg(ettersendingsoppgave = ettersendingsoppgave, tittel = "Vedlegg", skjemaId = "Q5", id = 0)
        val vedlegg2 = EttersendingsoppgaveVedlegg(ettersendingsoppgave = ettersendingsoppgave, tittel = "Vedlegg 2", skjemaId = "Q9", id = 1)
        ettersendingsoppgave.vedleggsliste.add(vedlegg)
        ettersendingsoppgave.vedleggsliste.add(vedlegg2)
        forsendelse.ettersendingsoppgave = ettersendingsoppgave
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        ettersendingsOppgaveService.slettEttersendingsoppave(SlettEttersendingsoppgave(1))

        forsendelse.ettersendingsoppgave.shouldBeNull()
    }

    @Test
    fun `skal opprette ettersendingsoppgave`() {
        val forsendelse = opprettForsendelse2(mottaker = Mottaker(ident = GJELDER_IDENT))
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        ettersendingsOppgaveService.opprettEttersendingsoppgave(
            OpprettEttersendingsoppgaveRequest(
                forsendelseId = 1,
                tittel = "Ny tittel",
                ettersendelseForJournalpostId = "333",
                skjemaId = "213213",
            ),
        )
        forsendelse.ettersendingsoppgave!!.tittel shouldBe "Ny tittel"
        forsendelse.ettersendingsoppgave!!.innsendingsfristDager shouldBe 21
        forsendelse.ettersendingsoppgave!!.ettersendelseForJournalpostId shouldBe "333"
        forsendelse.ettersendingsoppgave!!.skjemaId shouldBe "213213"
    }

    @Test
    fun `skal kaste exception hvis ettersendingsoppgave ikke finnes`() {
        val request =
            OppdaterEttersendingsoppgaveRequest(
                forsendelseId = 1L,
                tittel = "Oppdatert tittel",
                oppdaterDokument = null,
                innsendingsfristDager = 10,
                ettersendelseForJournalpostId = "123",
                skjemaId = "123",
            )
        val forsendelse = opprettForsendelse2(mottaker = Mottaker(ident = GJELDER_IDENT))
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        val exception =
            shouldThrow<HttpClientErrorException> {
                ettersendingsOppgaveService.oppdaterEttersendingsoppgave(request)
            }

        exception.message shouldContain "Fant ikke ettersendingsoppgave i forsendelse 1"
    }

    @Test
    fun `skal kaste exception hvis mottaker ikke er lik gjelder`() {
        val forsendelse = opprettForsendelse2(mottaker = Mottaker(ident = GJELDER_IDENT_BP))
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        val exception =
            shouldThrow<HttpClientErrorException> {
                ettersendingsOppgaveService.opprettEttersendingsoppgave(
                    OpprettEttersendingsoppgaveRequest(
                        forsendelseId = 1,
                        tittel = "Ny tittel",
                        ettersendelseForJournalpostId = "333",
                        skjemaId = "213213",
                    ),
                )
            }

        exception.message shouldContain "Kan ikke opprette ettersendingsoppgave hvis gjelder er ulik mottaker"
    }

    @Test
    fun `skal kaste exception hvis skjemaId ikke er satt`() {
        val request =
            OppdaterEttersendingsoppgaveRequest(
                forsendelseId = 1L,
                tittel = "Oppdatert tittel",
                oppdaterDokument = null,
                innsendingsfristDager = 10,
                ettersendelseForJournalpostId = "123",
            )
        val forsendelse = opprettForsendelse2(mottaker = Mottaker(ident = GJELDER_IDENT))
        every { forsendelseTjeneste.medForsendelseId(any()) } returns forsendelse

        val exception =
            shouldThrow<HttpClientErrorException> {
                ettersendingsOppgaveService.oppdaterEttersendingsoppgave(request)
            }

        exception.message shouldContain "skjemaId må være satt når ettersendelseForJournalpostId er satt"
    }
}
