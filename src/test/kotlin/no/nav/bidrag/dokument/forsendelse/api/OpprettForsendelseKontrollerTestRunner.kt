package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE1
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE2
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE3
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_BRUKSENHETSNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_LANDKODE
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_LANDKODE3
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_POSTNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_POSTSTED
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.JOURNALFØRENDE_ENHET
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.SAKSNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.SPRÅK_NORSK_BOKMÅL
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_1
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_2
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime


class OpprettForsendelseKontrollerTestRunner: KontrollerTestRunner() {

    @Test
    fun `Skal opprette forsendelse`(){

        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

            assertSoftly {
                forsendelse.dokumenter shouldHaveSize 2
                val hoveddokument = forsendelse.dokumenter.hoveddokument!!
                hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
                hoveddokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                hoveddokument.rekkefølgeIndeks shouldBe 0
                hoveddokument.tittel shouldBe TITTEL_HOVEDDOKUMENT
                hoveddokument.dokumentmalId shouldBe HOVEDDOKUMENT_DOKUMENTMAL
                hoveddokument.journalpostId shouldBe null
                hoveddokument.eksternDokumentreferanse shouldBe null
                hoveddokument.dokumentreferanse shouldBe "BIF${hoveddokument.dokumentId}"
                hoveddokument.tilknyttetSom shouldBe DokumentTilknyttetSom.HOVEDDOKUMENT

                val vedlegg = forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge[1]
                vedlegg.dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
                vedlegg.arkivsystem shouldBe DokumentArkivSystem.JOARK
                vedlegg.rekkefølgeIndeks shouldBe 1
                vedlegg.tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
                vedlegg.tittel shouldBe TITTEL_VEDLEGG_1
                vedlegg.journalpostId shouldBe "123123213"
                vedlegg.eksternDokumentreferanse shouldBe "123213"

                forsendelse.forsendelseType shouldBe ForsendelseType.UTGÅENDE
                forsendelse.status shouldBe ForsendelseStatus.UNDER_PRODUKSJON
                forsendelse.språk shouldBe SPRÅK_NORSK_BOKMÅL
                forsendelse.saksnummer shouldBe SAKSNUMMER
                forsendelse.opprettetAvIdent shouldBe SAKSBEHANDLER_IDENT
                forsendelse.opprettetAvNavn shouldBe SAKSBEHANDLER_NAVN
                forsendelse.opprettetTidspunkt shouldHaveSameDayAs LocalDateTime.now()
                forsendelse.endretTidspunkt shouldHaveSameDayAs LocalDateTime.now()
                forsendelse.enhet shouldBe JOURNALFØRENDE_ENHET
                forsendelse.endretAvIdent shouldBe SAKSBEHANDLER_IDENT

                forsendelse.mottaker shouldNotBe null

                val mottaker = forsendelse.mottaker!!
                mottaker.ident shouldBe MOTTAKER_IDENT
                mottaker.navn shouldBe MOTTAKER_NAVN
                mottaker.språk shouldBe SPRÅK_NORSK_BOKMÅL
                mottaker.identType shouldBe MottakerIdentType.FNR

                mottaker.adresse shouldNotBe null
                val adresse = mottaker.adresse!!
                adresse.adresselinje1 shouldBe ADRESSE_ADRESSELINJE1
                adresse.adresselinje2 shouldBe ADRESSE_ADRESSELINJE2
                adresse.adresselinje3 shouldBe ADRESSE_ADRESSELINJE3
                adresse.postnummer shouldBe ADRESSE_POSTNUMMER
                adresse.poststed shouldBe ADRESSE_POSTSTED
                adresse.bruksenhetsnummer shouldBe ADRESSE_BRUKSENHETSNUMMER
                adresse.landkode shouldBe ADRESSE_LANDKODE
                adresse.landkode3 shouldBe ADRESSE_LANDKODE3


                stubUtils.Valider().bestillDokumentKaltMed(HOVEDDOKUMENT_DOKUMENTMAL, "{" +
                        "\"mottaker\":" +
                        "{\"ident\":\"${mottaker.ident}\",\"navn\":\"${mottaker.navn}\",\"språk\":\"NB\"," +
                        "\"adresse\":{\"adresselinje1\":\"Adresselinje1\",\"adresselinje2\":\"Adresselinje2\",\"adresselinje3\":\"Adresselinje3\",\"bruksenhetsnummer\":\"H0305\",\"landkode\":\"NO\",\"landkode3\":\"NOR\",\"postnummer\":\"3040\",\"poststed\":\"Drammen\"}}," +
                        "\"saksbehandler\":null," +
                        "\"gjelderId\":\"${forsendelse.gjelderIdent}\"," +
                        "\"saksnummer\":\"${forsendelse.saksnummer}\"," +
                        "\"vedtaksId\":null," +
                        "\"dokumentreferanse\":\"${hoveddokument.dokumentreferanse}\"," +
                        "\"tittel\":\"${hoveddokument.tittel}\"," +
                        "\"enhet\":\"${forsendelse.enhet}\"," +
                        "\"språk\":\"${forsendelse.språk}\"}"
                )
            }

        }


        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
    }

    @Test
    fun `Skal opprette forsendelse som notat hvis forsendelsetype ikke er sendt med i forespørsel`(){

        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()
            .copy(forsendelseType = null, dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = DOKUMENTMAL_NOTAT
                )
            ))

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!
            forsendelse.forsendelseType shouldBe ForsendelseType.NOTAT

            forsendelse.dokumenter shouldHaveSize 1
            val hoveddokument = forsendelse.dokumenter.hoveddokument!!
            hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            hoveddokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        }
    }

    @Test
    fun `Skal opprette forsendelse og sette dokument status BESTILLING_FEILET når bestilling feiler`(){

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
    fun `Skal opprette forsendelse og legge til nytt dokument på opprettet forsendelse`(){

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
            tittel = "Tittel ny dokument",
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
        )
        val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(forsendelseId)!!

            forsendelse.dokumenter shouldHaveSize 2
            val nyDokument = forsendelse.dokumenter.find { it.tittel == "Tittel ny dokument" }!!
            nyDokument.tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
            nyDokument.rekkefølgeIndeks shouldBe forsendelse.dokumenter.size - 1
            nyDokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            nyDokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
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
                    dokumentreferanse = "54545545"
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

        val responseForsendelse = utførHentJournalpost(forsendelse.forsendelseId.toString())

        responseForsendelse.statusCode shouldBe HttpStatus.OK
        responseForsendelse.body!!.journalpost!!.dokumenter.size shouldBe 2
    }

}