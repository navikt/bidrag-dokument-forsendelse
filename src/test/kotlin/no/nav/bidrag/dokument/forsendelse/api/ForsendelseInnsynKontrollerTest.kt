package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.dto.AktorDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.JOURNALFØRENDE_ENHET
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_1
import no.nav.bidrag.dokument.forsendelse.utils.med
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate


class ForsendelseInnsynKontrollerTest: KontrollerTestRunner() {


    @Test
    fun `Skal hente forsendelse`(){
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!.journalpost!!

        assertSoftly {
            forsendelseResponse.innhold shouldBe TITTEL_HOVEDDOKUMENT
            forsendelseResponse.gjelderIdent shouldBe GJELDER_IDENT
            forsendelseResponse.gjelderAktor shouldBe AktorDto(GJELDER_IDENT)
            forsendelseResponse.avsenderMottaker shouldBe AvsenderMottakerDto(MOTTAKER_NAVN, MOTTAKER_IDENT)
            forsendelseResponse.brevkode?.kode shouldBe HOVEDDOKUMENT_DOKUMENTMAL
            forsendelseResponse.journalpostId shouldBe "BIF-${forsendelse.forsendelseId}"
            forsendelseResponse.journalstatus shouldBe "D"
            forsendelseResponse.dokumentType shouldBe "U"
            forsendelseResponse.journalforendeEnhet shouldBe JOURNALFØRENDE_ENHET
            forsendelseResponse.sakstilknytninger shouldContain SAKSNUMMER
            response.body!!.sakstilknytninger shouldContain SAKSNUMMER
            forsendelseResponse.fagomrade shouldBe "BID"
            forsendelseResponse.journalfortDato?.shouldHaveSameDayAs(LocalDate.now())
            forsendelseResponse.språk shouldBe "NB"
            forsendelseResponse.journalfortAv shouldBe SAKSBEHANDLER_IDENT
            forsendelseResponse.opprettetAvIdent shouldBe SAKSBEHANDLER_IDENT
            forsendelseResponse.dokumenter shouldHaveSize 1

            val hoveddokument = forsendelseResponse.dokumenter[0]
            hoveddokument.tittel shouldBe TITTEL_HOVEDDOKUMENT
            hoveddokument.arkivSystem shouldBe DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
            hoveddokument.journalpostId shouldBe "BID-123123"
            hoveddokument.dokumentmalId shouldBe HOVEDDOKUMENT_DOKUMENTMAL
            hoveddokument.dokumentreferanse shouldBe forsendelse.dokumenter[0].dokumentreferanse
        }
    }

    @Test
    fun `Skal returnere forsendelse med status F hvis forsendels er avbrutt`(){
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            med status ForsendelseStatus.AVBRUTT
            + nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        response.body!!.journalpost!!.journalstatus shouldBe "F"
        response.body!!.journalpost!!.feilfort shouldBe true
    }


    @Test
    fun `Skal hente forsendelse med dokumenter i riktig rekkefølge`(){
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, tilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, rekkefølgeIndeks = 1, tittel = "VEDLEGG1")
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, rekkefølgeIndeks = 2, tittel = "VEDLEGG2", eksternDokumentreferanse = "4543434")
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, rekkefølgeIndeks = 4, slettet = true, tittel = "VEDLEGG4", eksternDokumentreferanse = "3231312313")
            + nyttDokument(tilknyttetSom = DokumentTilknyttetSom.VEDLEGG, journalpostId = "BID-123123213", eksternDokumentreferanse = "12312321333", rekkefølgeIndeks = 3, tittel = "VEDLEGG3")
        }
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!.journalpost!!

        assertSoftly {
            val dokumenter = forsendelseResponse.dokumenter
            dokumenter shouldHaveSize 4
            dokumenter[0].tittel shouldBe "HOVEDDOK"
            dokumenter[1].tittel shouldBe "VEDLEGG1"
            dokumenter[2].tittel shouldBe "VEDLEGG2"
            dokumenter[3].tittel shouldBe "VEDLEGG3"
        }
    }

    @Test
    fun `Utgående forsendelse skal ha status KP hvis alle dokumenter er ferdigstilt`(){
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT)
            + nyttDokument(
                journalpostId = null,
                eksternDokumentreferanse = null,
                dokumentStatus = DokumentStatus.FERDIGSTILT,
                tilknyttetSom = DokumentTilknyttetSom.VEDLEGG,
                tittel = TITTEL_VEDLEGG_1
            )
        }
        val response =  utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!.journalpost!!

        assertSoftly {
            forsendelseResponse.journalstatus shouldBe "KP"

            forsendelseResponse.dokumenter shouldHaveSize 2
            val hoveddokumentForsendelse2 = forsendelseResponse.dokumenter[0]
            val vedleggForsendelse2 = forsendelseResponse.dokumenter[1]

            hoveddokumentForsendelse2.tittel shouldBe TITTEL_HOVEDDOKUMENT
            vedleggForsendelse2.tittel shouldBe TITTEL_VEDLEGG_1
            vedleggForsendelse2.dokumentreferanse shouldBe forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge[1].dokumentreferanse
        }
    }

    @Test
    fun `Skal hente forsendelser basert på saksnummer`(){
        val forsendelse1 = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING, tittel = "FORSENDELSE 1")
        }

        val forsendelse2 = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "FORSENDELSE 2")
        }

        testDataManager.opprettOgLagreForsendelse {
            med saksnummer "5435435"
            + nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }

        val response = httpHeaderTestRestTemplate.exchange("${rootUri()}/sak/${forsendelse1.saksnummer}/journal", HttpMethod.GET, null, object : ParameterizedTypeReference<List<JournalpostDto>>() {})

        response.statusCode shouldBe HttpStatus.OK

        val journalResponse = response.body!!

        assertSoftly {
            journalResponse shouldHaveSize 2

            val forsendelseResponse1 = journalResponse.find { it.journalpostId == forsendelse1.forsendelseIdMedPrefix }!!
            val forsendelseResponse2 = journalResponse.find { it.journalpostId == forsendelse2.forsendelseIdMedPrefix }!!

            forsendelseResponse1.innhold shouldBe "FORSENDELSE 1"
            forsendelseResponse2.innhold shouldBe "FORSENDELSE 2"
        }
    }

    @Test
    fun `Skal ikke hente forsendelser som er arkivert i fagarkivet (JOARK) eller har status AVBRUTT`(){
        val saksnummer = "3123213123213"
        val forsendelse1 = testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            + nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING, tittel = "FORSENDELSE 1")
        }

        val forsendelse2 = testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            + nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "FORSENDELSE 2")
        }

        testDataManager.opprettOgLagreForsendelse {
            med arkivJournalpostId "123123213"
            med saksnummer saksnummer
            + nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "FORSENDELSE 3")
        }

        val forsendelse3Avbrutt = testDataManager.opprettOgLagreForsendelse {
            med status ForsendelseStatus.AVBRUTT
            med saksnummer saksnummer
            + nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "FORSENDELSE 4")
        }

        val response = utførHentJournalForSaksnummer(forsendelse1.saksnummer)

        response.statusCode shouldBe HttpStatus.OK

        val journalResponse = response.body!!

        assertSoftly {
            journalResponse shouldHaveSize 3

            val forsendelseResponse1 = journalResponse.find { it.journalpostId == "BIF-${forsendelse1.forsendelseId}" }!!
            val forsendelseResponse2 = journalResponse.find { it.journalpostId == "BIF-${forsendelse2.forsendelseId}" }!!
            val forsendelseResponse3 = journalResponse.find { it.journalpostId == "BIF-${forsendelse3Avbrutt.forsendelseId}" }!!

            forsendelseResponse1.innhold shouldBe "FORSENDELSE 1"
            forsendelseResponse2.innhold shouldBe "FORSENDELSE 2"
            forsendelseResponse3.innhold shouldBe "FORSENDELSE 4"
        }
    }

}