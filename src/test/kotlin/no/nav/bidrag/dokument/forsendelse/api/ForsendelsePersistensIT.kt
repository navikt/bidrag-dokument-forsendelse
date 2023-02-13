package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration

class ForsendelsePersistensIT : KontrollerTestContainerRunner() {

    @Test
    fun `Skal opprette forsendelse`() {

        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)
            forsendelse shouldNotBe null
            forsendelse!!.tema shouldBe ForsendelseTema.BID
        }


        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
    }

    @Test
    fun `Skal opprette forsendelse med tema FAR`() {

        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(tema = "FAR")

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)
            forsendelse shouldNotBe null
            forsendelse!!.tema shouldBe ForsendelseTema.FAR
        }


        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
    }

    @Test
    fun `Skal oppdatere og endre rekkefølge på dokumentene i forsendelse`() {

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
            +nyttDokument(rekkefølgeIndeks = 1)
            +nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2)
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
}