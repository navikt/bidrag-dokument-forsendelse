package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentTilknyttetSomTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hoveddokumentFørst
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.vedlegger
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_1
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_2
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.Duration
import java.time.LocalDate


class OpprettForsendelseKontrollerTest: AbstractKontrollerTest() {

    @Test
    fun `Skal opprette forsendelse`(){

        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

            forsendelse.dokumenter shouldHaveSize 2
            val hoveddokument = forsendelse.dokumenter.hoveddokument!!
            hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            hoveddokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER

            val vedlegg = forsendelse.dokumenter.hoveddokumentFørst[1]
            vedlegg.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            vedlegg.arkivsystem shouldBe DokumentArkivSystem.JOARK
        }

        val forsendelseResponse = httpHeaderTestRestTemplate.exchange("${rootUri()}/journal/${response.body!!.forsendelseId}", HttpMethod.GET, null, JournalpostResponse::class.java)
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
    }

    @Test
    fun `Skal opprette forsendelse og sette status BESTILLING_FEILET på dokument når bestilling feiler`(){

        stubUtils.stubBestillDokumentFeiler()

        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

            forsendelse.dokumenter shouldHaveSize 2
            val hoveddokument = forsendelse.dokumenter.hoveddokument!!
            hoveddokument.dokumentStatus shouldBe DokumentStatus.BESTILLING_FEILET
            hoveddokument.arkivsystem shouldBe DokumentArkivSystem.UKJENT
        }
    }

    @Test
    fun `Skal opprette forsendelse og legge til ny dokument på opprettet forsendelse`(){

        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(dokumenter = listOf(
            OpprettDokumentForespørsel(
                tittel = TITTEL_VEDLEGG_1,
                dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                journalpostId = "JOARK-123123213",
                dokumentreferanse = "123213"
            )
        ))

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelseId = response.body!!.forsendelseId!!

        val forsendelseMedEnDokument = testDataManager.hentForsendelse(forsendelseId)!!

        forsendelseMedEnDokument.dokumenter shouldHaveSize 1
        val dokument = forsendelseMedEnDokument.dokumenter[0]
        dokument.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
        dokument.arkivsystem shouldBe DokumentArkivSystem.JOARK

        val opprettDokumentForespørsel = OpprettDokumentForespørsel(
            tittel = TITTEL_HOVEDDOKUMENT,
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
            tilknyttetSom = DokumentTilknyttetSomTo.HOVEDDOKUMENT
        )
        val responseNyDokument = httpHeaderTestRestTemplate.exchange("${rootUri()}/$forsendelseId/dokument", HttpMethod.POST, HttpEntity(opprettDokumentForespørsel), DokumentRespons::class.java)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(forsendelseId)!!

            forsendelse.dokumenter shouldHaveSize 2
            val hoveddokument = forsendelse.dokumenter.hoveddokument!!
            hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            hoveddokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        }
    }

    @Test
    fun `Skal opprette forsendelse og fjerne dokument fra opprettet forsendelse`(){

        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
                ),
                OpprettDokumentForespørsel(
                    tittel = TITTEL_VEDLEGG_1,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    journalpostId = "JOARK-123123213",
                    dokumentreferanse = "123213"
                ),
                OpprettDokumentForespørsel(
                    tittel = TITTEL_VEDLEGG_2,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    journalpostId = "JOARK-555454",
                    dokumentreferanse = "123213"
                )
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelseId = response.body!!.forsendelseId!!

        val forsendelseMedEnDokument = testDataManager.hentForsendelse(forsendelseId)!!
        forsendelseMedEnDokument.dokumenter shouldHaveSize 3
        val dokumentSomSkalSlettes = forsendelseMedEnDokument.dokumenter.hoveddokument!!

        val responseNyDokument = utførSlettDokumentForespørsel(forsendelseId, dokumentSomSkalSlettes.dokumentreferanse)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        forsendelse.dokumenter shouldHaveSize 3
        val slettetDokument = forsendelse.dokumenter.find { it.dokumentId == dokumentSomSkalSlettes.dokumentId }!!
        slettetDokument.tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
        slettetDokument.slettetTidspunkt!! shouldHaveSameDayAs LocalDate.now()

        val nyHoveddokument = forsendelse.dokumenter.hoveddokument
        nyHoveddokument shouldNotBe null
        nyHoveddokument!!.tittel shouldBe TITTEL_VEDLEGG_1
        nyHoveddokument.slettetTidspunkt shouldBe null

        val vedlegger = forsendelse.dokumenter.vedlegger
        vedlegger shouldHaveSize 1
        vedlegger[0].tittel shouldBe TITTEL_VEDLEGG_2
        vedlegger[0].slettetTidspunkt shouldBe null

        val responseForsendelse = httpHeaderTestRestTemplate.exchange("${rootUri()}/journal/${forsendelse.forsendelseId}", HttpMethod.GET, null, JournalpostResponse::class.java)

        responseForsendelse.statusCode shouldBe HttpStatus.OK
        responseForsendelse.body!!.journalpost!!.dokumenter.size shouldBe 2
    }

}