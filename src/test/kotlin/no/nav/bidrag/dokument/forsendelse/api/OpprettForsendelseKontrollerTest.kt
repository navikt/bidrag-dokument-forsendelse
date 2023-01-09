package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hoveddokumentFørst
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import org.awaitility.kotlin.await
import org.hibernate.Hibernate
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.Duration
import javax.transaction.Transactional
import kotlin.test.assertFails


class OpprettForsendelseKontrollerTest: AbstractKontrollerTest() {

    @Test
    fun `Skal opprette forsendelse`(){

        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()

        val response = httpHeaderTestRestTemplate.exchange(rootUri(), HttpMethod.POST, HttpEntity(opprettForsendelseForespørsel), OpprettForsendelseRespons::class.java)
        response.statusCode shouldBe HttpStatus.OK


        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

            Hibernate.initialize(forsendelse.dokumenter)
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

}