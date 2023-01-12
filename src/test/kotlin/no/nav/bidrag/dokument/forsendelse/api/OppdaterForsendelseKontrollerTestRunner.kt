package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate


class OppdaterForsendelseKontrollerTestRunner: KontrollerTestRunner() {

    @Test
    fun `Skal oppdatere og endre rekkefølge på dokumentene i forsendelse`(){

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, tilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT, rekkefølgeIndeks = 0)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, rekkefølgeIndeks = 1)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, journalpostId = "BID-123123213", eksternDokumentreferanse = "12312321333", rekkefølgeIndeks = 2)
        }

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

        val oppdaterForespørsel = OppdaterForsendelseForespørsel(
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        tittel = vedlegg1.tittel,
                        dokumentreferanse = vedlegg1.dokumentreferanse
                    ),
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel hoveddok",
                        dokumentreferanse = hoveddokument.dokumentreferanse
                    ),
                    OppdaterDokumentForespørsel(
                        tittel = vedlegg2.tittel,
                        dokumentreferanse = vedlegg2.dokumentreferanse
                    )
                )
        )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 3
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe vedlegg1.tittel
            oppdatertForsendelse.dokumenter.vedlegger[0].tittel shouldBe "Ny tittel hoveddok"
            oppdatertForsendelse.dokumenter.vedlegger[1].tittel shouldBe vedlegg2.tittel

            oppdatertForsendelse.dokumenter.hoveddokument!!.rekkefølgeIndeks shouldBe 0
            oppdatertForsendelse.dokumenter.vedlegger[0].rekkefølgeIndeks shouldBe 1
            oppdatertForsendelse.dokumenter.vedlegger[1].rekkefølgeIndeks shouldBe 2

            oppdatertForsendelse.dokumenter.hoveddokument!!.tilknyttetSom shouldBe DokumentTilknyttetSom.HOVEDDOKUMENT
            oppdatertForsendelse.dokumenter.vedlegger[0].tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
            oppdatertForsendelse.dokumenter.vedlegger[1].tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
        }
    }

    @Test
    fun `Oppdater skal feile hvis ikke alle dokumenter er inkludert i forespørselen`(){

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, tilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT, rekkefølgeIndeks = 0)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, rekkefølgeIndeks = 1)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, journalpostId = "BID-123123213", eksternDokumentreferanse = "12312321333", rekkefølgeIndeks = 2)
        }

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

        val oppdaterForespørsel = OppdaterForsendelseForespørsel(
            dokumenter = listOf(
                OppdaterDokumentForespørsel(
                    tittel = vedlegg1.tittel,
                    dokumentreferanse = "123133313123123123"
                ),
                OppdaterDokumentForespørsel(
                    tittel = "Ny tittel hoveddok",
                    dokumentreferanse = hoveddokument.dokumentreferanse
                ),
                OppdaterDokumentForespørsel(
                    tittel = vedlegg2.tittel,
                    dokumentreferanse = vedlegg2.dokumentreferanse
                )
            )
        )
        val respons = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `Oppdater skal feile hvis samme dokumentent er inkludert flere ganger i forespørselen`(){

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, tilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT, rekkefølgeIndeks = 0)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, rekkefølgeIndeks = 1)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, journalpostId = "BID-123123213", eksternDokumentreferanse = "12312321333", rekkefølgeIndeks = 2)
        }

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

        val oppdaterForespørsel = OppdaterForsendelseForespørsel(
            dokumenter = listOf(
                OppdaterDokumentForespørsel(
                    tittel = vedlegg1.tittel,
                    dokumentreferanse = hoveddokument.dokumentreferanse
                ),
                OppdaterDokumentForespørsel(
                    tittel = "Ny tittel hoveddok",
                    dokumentreferanse = hoveddokument.dokumentreferanse
                ),
                OppdaterDokumentForespørsel(
                    tittel = vedlegg2.tittel,
                    dokumentreferanse = vedlegg2.dokumentreferanse
                )
            )
        )
        val respons = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `Skal slette dokument med ekstern referanse fra forsendelse`(){

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, tilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT, rekkefølgeIndeks = 0)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, rekkefølgeIndeks = 1)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, journalpostId = "BID-123123213", eksternDokumentreferanse = "12312321333", rekkefølgeIndeks = 2)
        }

        val forsendelseId = forsendelse.forsendelseId!!

        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

        val responseNyDokument = utførSlettDokumentForespørsel(forsendelseId, vedlegg1.dokumentreferanse)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe hoveddokument.tittel
            oppdatertForsendelse.dokumenter.vedlegger[0].tittel shouldBe vedlegg2.tittel

            oppdatertForsendelse.dokumenter.hoveddokument!!.rekkefølgeIndeks shouldBe 0
            oppdatertForsendelse.dokumenter.vedlegger[0].rekkefølgeIndeks shouldBe 1

            oppdatertForsendelse.dokumenter.hoveddokument!!.tilknyttetSom shouldBe DokumentTilknyttetSom.HOVEDDOKUMENT
            oppdatertForsendelse.dokumenter.vedlegger[0].tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
        }

        val respons = utførHentJournalpost(forsendelseId.toString())
        respons.statusCode shouldBe HttpStatus.OK

        val forsendelseFraRespons = respons.body!!.journalpost!!

        assertSoftly {
            forsendelseFraRespons.dokumenter shouldHaveSize 2
            forsendelseFraRespons.dokumenter[0].tittel shouldBe hoveddokument.tittel
            forsendelseFraRespons.dokumenter[1].tittel shouldBe vedlegg2.tittel
        }
    }

    @Test
    fun `Skal slette hoveddokument uten ekstern referanse fra forsendelse`(){

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, tilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT, rekkefølgeIndeks = 0)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, rekkefølgeIndeks = 1)
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, journalpostId = "BID-123123213", eksternDokumentreferanse = "12312321333", rekkefølgeIndeks = 2)
        }

        val forsendelseId = forsendelse.forsendelseId!!

        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

        val responseNyDokument = utførSlettDokumentForespørsel(forsendelseId, hoveddokument.dokumentreferanse)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 3

            val dokumenter = oppdatertForsendelse.dokumenter.sortertEtterRekkefølge
            dokumenter[0].tittel shouldBe vedlegg1.tittel
            dokumenter[1].tittel shouldBe vedlegg2.tittel
            dokumenter[2].tittel shouldBe hoveddokument.tittel
            dokumenter[2].slettetTidspunkt shouldNotBe null
            dokumenter[2].slettetTidspunkt!! shouldHaveSameDayAs LocalDate.now()

            dokumenter[0].rekkefølgeIndeks shouldBe 0
            dokumenter[1].rekkefølgeIndeks shouldBe 1
            dokumenter[2].rekkefølgeIndeks shouldBe 2
        }


        val respons = utførHentJournalpost(forsendelseId.toString())
        respons.statusCode shouldBe HttpStatus.OK

        val forsendelseFraRespons = respons.body!!.journalpost!!

        assertSoftly {
            forsendelseFraRespons.dokumenter shouldHaveSize 2
            forsendelseFraRespons.dokumenter[0].tittel shouldBe vedlegg1.tittel
            forsendelseFraRespons.dokumenter[1].tittel shouldBe vedlegg2.tittel
        }
    }

    @Test
    fun `Skal feile hvis eksisterende dokumentreferanse blir forsøkt lagt til på forsendelse`(){

        val dokument = nyttDokument()
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + dokument
        }

        val forsendelseId = forsendelse.forsendelseId!!

        val opprettDokumentForespørsel = OpprettDokumentForespørsel(
            tittel = TITTEL_HOVEDDOKUMENT,
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
            dokumentreferanse = dokument.dokumentreferanse,
            journalpostId = "JOARK-${dokument.journalpostId}"
        )
        val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.BAD_REQUEST
        responseNyDokument.headers["Warning"]!![0] shouldBe "Forespørselen inneholder ugyldig data: Forsendelse $forsendelseId har allerede tilknyttet dokument med dokumentreferanse 123213213"
    }

}