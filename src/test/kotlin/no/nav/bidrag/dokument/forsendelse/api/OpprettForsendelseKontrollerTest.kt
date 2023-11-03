package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import no.nav.bidrag.dokument.forsendelse.api.dto.BehandlingInfoDto
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerIdentTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE1
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE2
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE3
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_BRUKSENHETSNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_LANDKODE
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_LANDKODE3
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_POSTNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_POSTSTED
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_2
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT_BM
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.JOURNALFØRENDE_ENHET
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.SAKSNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.SAMHANDLER_ID
import no.nav.bidrag.dokument.forsendelse.utils.SPRÅK_NORSK_BOKMÅL
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_1
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_2
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import no.nav.bidrag.domene.enums.Stønadstype
import no.nav.bidrag.domene.enums.SøktAvType
import no.nav.bidrag.domene.enums.Vedtakstype
import no.nav.bidrag.transport.dokument.DokumentStatusDto
import no.nav.bidrag.transport.dokument.JournalpostStatus
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class OpprettForsendelseKontrollerTest : KontrollerTestRunner() {

    @Test
    fun `Skal opprette forsendelse`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(batchId = "FB050")

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

            assertSoftly {
                forsendelse.dokumenter shouldHaveSize 2
                val hoveddokument = forsendelse.dokumenter.hoveddokument!!
                hoveddokument.språk shouldBe forsendelse.språk
                hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
                hoveddokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                hoveddokument.rekkefølgeIndeks shouldBe 0
                hoveddokument.tittel shouldBe TITTEL_HOVEDDOKUMENT
                hoveddokument.dokumentmalId shouldBe HOVEDDOKUMENT_DOKUMENTMAL
                hoveddokument.journalpostId shouldBe null
                hoveddokument.dokumentreferanseOriginal shouldBe null
                hoveddokument.dokumentreferanse shouldBe "BIF${hoveddokument.dokumentId}"
                hoveddokument.tilknyttetSom shouldBe DokumentTilknyttetSom.HOVEDDOKUMENT

                val vedlegg = forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge[1]
                vedlegg.språk shouldBe forsendelse.språk
                vedlegg.dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
                vedlegg.arkivsystem shouldBe DokumentArkivSystem.JOARK
                vedlegg.rekkefølgeIndeks shouldBe 1
                vedlegg.tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
                vedlegg.tittel shouldBe TITTEL_VEDLEGG_1
                vedlegg.journalpostId shouldBe "JOARK-123123213"
                vedlegg.dokumentreferanseOriginal shouldBe "123213"

                forsendelse.forsendelseType shouldBe ForsendelseType.UTGÅENDE
                forsendelse.status shouldBe ForsendelseStatus.UNDER_PRODUKSJON
                forsendelse.språk shouldBe SPRÅK_NORSK_BOKMÅL
                forsendelse.batchId shouldBe "FB050"
                forsendelse.saksnummer shouldBe SAKSNUMMER
                forsendelse.opprettetAvIdent shouldBe SAKSBEHANDLER_IDENT
                forsendelse.opprettetAvNavn shouldBe SAKSBEHANDLER_NAVN
                forsendelse.opprettetTidspunkt shouldHaveSameDayAs LocalDateTime.now()
                forsendelse.endretTidspunkt shouldHaveSameDayAs LocalDateTime.now()
                forsendelse.enhet shouldBe JOURNALFØRENDE_ENHET
                forsendelse.endretAvIdent shouldBe SAKSBEHANDLER_IDENT

                forsendelse.mottaker shouldNotBe null
                forsendelse.tittel shouldBe null

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

                stubUtils.Valider().bestillDokumentKaltMed(
                    HOVEDDOKUMENT_DOKUMENTMAL,
                    "{" +
                            "\"mottaker\":" +
                            "{\"ident\":\"${mottaker.ident}\",\"navn\":\"${mottaker.navn}\",\"språk\":\"NB\"," +
                            "\"adresse\":{\"adresselinje1\":\"Adresselinje1\",\"adresselinje2\":\"Adresselinje2\",\"adresselinje3\":\"Adresselinje3\",\"bruksenhetsnummer\":\"H0305\",\"landkode\":\"NO\",\"landkode3\":\"NOR\",\"postnummer\":\"3040\",\"poststed\":\"Drammen\"}}," +
                            "\"saksbehandler\":null," +
                            "\"gjelderId\":\"${forsendelse.gjelderIdent}\"," +
                            "\"saksnummer\":\"${forsendelse.saksnummer}\"," +
                            "\"vedtakId\":null,\"behandlingId\":null," +
                            "\"dokumentreferanse\":\"${hoveddokument.dokumentreferanse}\"," +
                            "\"tittel\":\"${hoveddokument.tittel}\"," +
                            "\"enhet\":\"${forsendelse.enhet}\"," +
                            "\"språk\":\"${forsendelse.språk}\"," +
                            "\"barnIBehandling\":[]}"
                )
            }
        }

        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
        journalpost.innhold shouldBe journalpost.hentHoveddokument()?.tittel
    }

    @Test
    fun `Skal opprette forsendelse med barn i behandling`() {
        val opprettForsendelseForespørsel =
            nyOpprettForsendelseForespørsel().copy(batchId = "FB050", behandlingInfo = BehandlingInfoDto(barnIBehandling = listOf("123123123123")))

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

            assertSoftly {
                forsendelse.dokumenter shouldHaveSize 2
                val hoveddokument = forsendelse.dokumenter.hoveddokument!!
                val mottaker = forsendelse.mottaker!!
                forsendelse.behandlingInfo!!.barnIBehandling shouldContain "123123123123"
                stubUtils.Valider().bestillDokumentKaltMed(
                    HOVEDDOKUMENT_DOKUMENTMAL,
                    "{" +
                            "\"mottaker\":" +
                            "{\"ident\":\"${mottaker.ident}\",\"navn\":\"${mottaker.navn}\",\"språk\":\"NB\"," +
                            "\"adresse\":{\"adresselinje1\":\"Adresselinje1\",\"adresselinje2\":\"Adresselinje2\",\"adresselinje3\":\"Adresselinje3\",\"bruksenhetsnummer\":\"H0305\",\"landkode\":\"NO\",\"landkode3\":\"NOR\",\"postnummer\":\"3040\",\"poststed\":\"Drammen\"}}," +
                            "\"saksbehandler\":null," +
                            "\"gjelderId\":\"${forsendelse.gjelderIdent}\"," +
                            "\"saksnummer\":\"${forsendelse.saksnummer}\"," +
                            "\"vedtakId\":null,\"behandlingId\":null," +
                            "\"dokumentreferanse\":\"${hoveddokument.dokumentreferanse}\"," +
                            "\"tittel\":\"${hoveddokument.tittel}\"," +
                            "\"enhet\":\"${forsendelse.enhet}\"," +
                            "\"språk\":\"${forsendelse.språk}\"," +
                            "\"barnIBehandling\":[\"123123123123\"]}"
                )
            }
        }

        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
        journalpost.innhold shouldBe journalpost.hentHoveddokument()?.tittel
    }

    @Test
    fun `Skal opprette forsendelse med behandlingsdetaljer uten forsendelse tittel`() {
        val soknadId = "123213"
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            batchId = "FB050",
            behandlingInfo = BehandlingInfoDto(
                soknadId = soknadId,
                erFattetBeregnet = true,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                stonadType = Stønadstype.FORSKUDD,
                vedtakType = Vedtakstype.FASTSETTELSE
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

            assertSoftly {
                forsendelse.dokumenter shouldHaveSize 2
                val behandlingInfo = forsendelse.behandlingInfo!!
                behandlingInfo.soknadId shouldBe soknadId
                behandlingInfo.erFattetBeregnet shouldBe true
                behandlingInfo.soknadFra shouldBe SoknadFra.BIDRAGSMOTTAKER
                behandlingInfo.stonadType shouldBe Stønadstype.FORSKUDD
                behandlingInfo.vedtakType shouldBe Vedtakstype.FASTSETTELSE
                behandlingInfo.behandlingType shouldBe null
                forsendelse.tittel shouldBe null
            }
        }

        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
        journalpost.innhold shouldBe journalpost.hentHoveddokument()?.tittel
    }

    @Test
    @Disabled
    fun `Skal opprette forsendelse med samhandlerid som mangler poststed`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            mottaker = MottakerTo(
                ident = SAMHANDLER_ID,
                identType = MottakerIdentTypeTo.SAMHANDLER,
                adresse = MottakerAdresseTo(
                    adresselinje1 = "Adresselinje1",
                    postnummer = "2306",
                    landkode3 = "NOR"
                )
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

        assertSoftly {
            forsendelse.mottaker shouldNotBe null

            val mottaker = forsendelse.mottaker!!
            mottaker.ident shouldBe SAMHANDLER_ID
            mottaker.navn shouldBe MOTTAKER_NAVN
            mottaker.språk shouldBe SPRÅK_NORSK_BOKMÅL
            mottaker.identType shouldBe MottakerIdentType.SAMHANDLER

            mottaker.adresse shouldNotBe null
            mottaker.adresse?.landkode shouldBe "NO"
            mottaker.adresse?.poststed shouldBe "HAMAR"
            mottaker.adresse?.postnummer shouldBe "2306"

            stubUtils.Valider().hentPersonKaltMed(SAMHANDLER_ID)
            stubUtils.Valider().hentPersonSpråkIkkeKaltMed(SAMHANDLER_ID)
        }

        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
        journalpost.avsenderMottaker!!.ident shouldBe SAMHANDLER_ID
        journalpost.avsenderMottaker?.navn shouldBe MOTTAKER_NAVN
    }

    @Test
    fun `Skal opprette forsendelse med samhandlerid`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            mottaker = MottakerTo(
                ident = SAMHANDLER_ID,
                identType = MottakerIdentTypeTo.SAMHANDLER
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

        assertSoftly {
            forsendelse.mottaker shouldNotBe null

            val mottaker = forsendelse.mottaker!!
            mottaker.ident shouldBe SAMHANDLER_ID
            mottaker.navn shouldBe MOTTAKER_NAVN
            mottaker.språk shouldBe SPRÅK_NORSK_BOKMÅL
            mottaker.identType shouldBe MottakerIdentType.SAMHANDLER

            mottaker.adresse shouldBe null

            stubUtils.Valider().hentPersonKaltMed(SAMHANDLER_ID)
            stubUtils.Valider().hentPersonSpråkIkkeKaltMed(SAMHANDLER_ID)
        }

        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
        journalpost.avsenderMottaker!!.ident shouldBe SAMHANDLER_ID
        journalpost.avsenderMottaker?.navn shouldBe MOTTAKER_NAVN
    }

    @Test
    fun `Skal opprette tom forsendelse med behandlingdetaljer`() {
        stubUtils.stubHentSak()
        val soknadId = "123213"
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            gjelderIdent = GJELDER_IDENT_BM,
            dokumenter = emptyList(),
            behandlingInfo = BehandlingInfoDto(
                soknadId = "123213",
                erFattetBeregnet = true,
                soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                stonadType = Stønadstype.FORSKUDD,
                vedtakType = Vedtakstype.FASTSETTELSE
            ),
            opprettTittel = true
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

        assertSoftly {
            forsendelse.mottaker shouldNotBe null
            forsendelse.dokumenter shouldHaveSize 0
            forsendelse.behandlingInfo shouldNotBe null
            val behandlingInfo = forsendelse.behandlingInfo!!
            behandlingInfo.soknadId shouldBe soknadId
            behandlingInfo.erFattetBeregnet shouldBe true
            behandlingInfo.soknadFra shouldBe SoknadFra.BIDRAGSMOTTAKER
            behandlingInfo.stonadType shouldBe Stønadstype.FORSKUDD
            behandlingInfo.vedtakType shouldBe Vedtakstype.FASTSETTELSE
            behandlingInfo.behandlingType shouldBe null
            forsendelse.tittel shouldBe "Vedtak om bidragsforskudd til bidragsmottaker"

            stubUtils.Valider().hentPersonKaltMed(MOTTAKER_IDENT)
            stubUtils.Valider().hentPersonSpråkIkkeKaltMed(MOTTAKER_IDENT)
        }

        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.status shouldBe JournalpostStatus.UNDER_OPPRETTELSE
        journalpost.innhold shouldBe "Vedtak om bidragsforskudd til bidragsmottaker"
    }

    @Test
    fun `Skal opprette forsendelse uten mottakernavn`() {
        val opprettForsendelseForespørsel =
            nyOpprettForsendelseForespørsel().copy(mottaker = MottakerTo(ident = MOTTAKER_IDENT))

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

        assertSoftly {
            forsendelse.mottaker shouldNotBe null

            val mottaker = forsendelse.mottaker!!
            mottaker.ident shouldBe MOTTAKER_IDENT
            mottaker.navn shouldBe MOTTAKER_NAVN
            mottaker.språk shouldBe SPRÅK_NORSK_BOKMÅL
            mottaker.identType shouldBe MottakerIdentType.FNR

            mottaker.adresse shouldBe null

            stubUtils.Valider().hentPersonKaltMed(MOTTAKER_IDENT)
            stubUtils.Valider().hentPersonSpråkIkkeKaltMed(MOTTAKER_IDENT)
        }

        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
    }

    @Test
    fun `Skal opprette forsendelse med mottakerspråk hvis språk ikke er satt`() {
        stubUtils.stubHentPersonSpraak("EN")
        val opprettForsendelseForespørsel =
            nyOpprettForsendelseForespørsel().copy(språk = null)

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

        assertSoftly {
            forsendelse.mottaker shouldNotBe null

            val mottaker = forsendelse.mottaker!!
            mottaker.ident shouldBe MOTTAKER_IDENT
            mottaker.navn shouldBe MOTTAKER_NAVN
            mottaker.språk shouldBe "EN"
            mottaker.identType shouldBe MottakerIdentType.FNR

            stubUtils.Valider().hentPersonKaltMed(MOTTAKER_IDENT)
            stubUtils.Valider().hentPersonSpråkKaltMed(MOTTAKER_IDENT)

            stubUtils.Valider().bestillDokumentKaltMed(
                HOVEDDOKUMENT_DOKUMENTMAL,
                "\"språk\":\"EN\""
            )
        }

        val forsendelseResponse = utførHentJournalpost(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!.journalpost
        forsendelseResponse.body!!.journalpost shouldNotBe null
        journalpost!!.dokumenter[0].status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
    }

    @Test
    fun `Skal opprette forsendelse som notat`() {
        val soknadId = "12321321"
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()
            .copy(
                behandlingInfo = BehandlingInfoDto(
                    soknadId = soknadId,
                    erFattetBeregnet = true,
                    soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                    stonadType = Stønadstype.EKTEFELLEBIDRAG,
                    vedtakType = Vedtakstype.FASTSETTELSE
                ),
                dokumenter = listOf(
                    OpprettDokumentForespørsel(
                        tittel = "Ektefellebidrag, Tittel notat",
                        dokumentmalId = DOKUMENTMAL_NOTAT
                    )
                ),
                opprettTittel = true
            )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!
        forsendelse.forsendelseType shouldBe ForsendelseType.NOTAT

        forsendelse.dokumenter shouldHaveSize 1
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
        hoveddokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        hoveddokument.tittel shouldBe "Ektefellebidrag, Tittel notat"
    }

    @Test
    @Disabled
    fun `Skal opprette forsendelse som notat hvis dokumentlisten inneholder mal med type notat`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()
            .copy(
                dokumenter = listOf(
                    OpprettDokumentForespørsel(
                        tittel = "Tittel notat",
                        dokumentmalId = DOKUMENTMAL_NOTAT
                    )
                )
            )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!
        forsendelse.forsendelseType shouldBe ForsendelseType.NOTAT

        forsendelse.dokumenter shouldHaveSize 1
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
        hoveddokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        hoveddokument.tittel shouldBe "Tittel notat"
    }

    @Test
    @Disabled
    fun `Skal opprette notat med prefiks tittel som inneholder klage`() {
        val soknadId = "12321321"
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()
            .copy(
                opprettTittel = true,
                behandlingInfo = BehandlingInfoDto(
                    soknadId = soknadId,
                    erFattetBeregnet = true,
                    soknadFra = SøktAvType.BIDRAGSMOTTAKER,
                    stonadType = Stønadstype.EKTEFELLEBIDRAG,
                    vedtakType = Vedtakstype.KLAGE
                ),
                dokumenter = listOf(
                    OpprettDokumentForespørsel(
                        tittel = "Tittel notat",
                        dokumentmalId = DOKUMENTMAL_NOTAT
                    )
                )
            )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!
        forsendelse.forsendelseType shouldBe ForsendelseType.NOTAT

        forsendelse.dokumenter shouldHaveSize 1
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
        hoveddokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        hoveddokument.tittel shouldBe "Ektefellebidrag klage, Tittel notat"
    }

    @Test
    fun `Skal ikke kunne opprette notat med flere dokumeenter`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel()
            .copy(
                dokumenter = listOf(
                    OpprettDokumentForespørsel(
                        tittel = TITTEL_HOVEDDOKUMENT,
                        dokumentmalId = DOKUMENTMAL_NOTAT
                    ),
                    OpprettDokumentForespørsel(
                        tittel = TITTEL_VEDLEGG_1,
                        dokumentmalId = DOKUMENTMAL_UTGÅENDE
                    )
                )
            )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.headers["Warning"]?.get(0) shouldContain "Kan ikke opprette ny forsendelse med flere dokumenter hvis forsendelsetype er Notat"
    }

    @Test
    fun `Skal opprette forsendelse og sette dokument status BESTILLING_FEILET når bestilling feiler`() {
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
    fun `Skal opprette forsendelse og ikke bestille dokument hvis bestillDokument er false`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    bestillDokument = false
                )
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

            forsendelse.dokumenter shouldHaveSize 1
            val hoveddokument = forsendelse.dokumenter.hoveddokument!!
            hoveddokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            hoveddokument.arkivsystem shouldBe DokumentArkivSystem.UKJENT

            stubUtils.Valider().bestillDokumentIkkeKalt(HOVEDDOKUMENT_DOKUMENTMAL)
        }
    }

    @Test
    fun `Skal opprette forsendelse og legge til nytt dokument på opprettet forsendelse med tittel som inneholder ugyldig tegn`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = "$TITTEL_VEDLEGG_1\u001A\u0085",
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    journalpostId = "JOARK-123123213",
                    dokumentreferanse = "123213"
                )
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelseId = response.body!!.forsendelseId!!

        val forsendelseMedEnDokument = testDataManager.hentForsendelse(forsendelseId)!!

        forsendelseMedEnDokument.dokumenter shouldHaveSize 1
        val dokument = forsendelseMedEnDokument.dokumenter[0]
        dokument.dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
        dokument.arkivsystem shouldBe DokumentArkivSystem.JOARK
        dokument.tittel shouldBe TITTEL_VEDLEGG_1

        val opprettDokumentForespørsel = OpprettDokumentForespørsel(
            tittel = "Tittel ny dokument\u001A",
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
        )
        val responseNyDokument =
            utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
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
    fun `Skal opprette forsendelse og legge til nytt dokument på opprettet forsendelse`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_VEDLEGG_1,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    journalpostId = "JOARK-123123213",
                    dokumentreferanse = "123213"
                )
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelseId = response.body!!.forsendelseId!!

        val forsendelseMedEnDokument = testDataManager.hentForsendelse(forsendelseId)!!

        forsendelseMedEnDokument.dokumenter shouldHaveSize 1
        val dokument = forsendelseMedEnDokument.dokumenter[0]
        dokument.dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
        dokument.arkivsystem shouldBe DokumentArkivSystem.JOARK

        val opprettDokumentForespørsel = OpprettDokumentForespørsel(
            tittel = "Tittel ny dokument",
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
        )
        val responseNyDokument =
            utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(forsendelseId)!!

            forsendelse.dokumenter shouldHaveSize 2
            val nyDokument = forsendelse.dokumenter.find { it.tittel == "Tittel ny dokument" }!!
            nyDokument.tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
            nyDokument.rekkefølgeIndeks shouldBe forsendelse.dokumenter.size - 1
            nyDokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            nyDokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
            responseNyDokument.body!!.dokumentreferanse shouldBe nyDokument.dokumentreferanse
        }
    }

    @Test
    fun `Skal opprette forsendelse uten bestille dokument og legge til nytt dokument uten å bestille dokument`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_VEDLEGG_1,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    journalpostId = "JOARK-123123213",
                    dokumentreferanse = "123213",
                    bestillDokument = false
                )
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelseId = response.body!!.forsendelseId!!

        val forsendelseMedEnDokument = testDataManager.hentForsendelse(forsendelseId)!!

        forsendelseMedEnDokument.dokumenter shouldHaveSize 1
        val dokument = forsendelseMedEnDokument.dokumenter[0]
        dokument.dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
        dokument.arkivsystem shouldBe DokumentArkivSystem.JOARK

        val opprettDokumentForespørsel = OpprettDokumentForespørsel(
            tittel = "Tittel ny dokument",
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
            språk = "EN",
            bestillDokument = false
        )
        val responseNyDokument =
            utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(forsendelseId)!!

            forsendelse.dokumenter shouldHaveSize 2
            val nyDokument = forsendelse.dokumenter.find { it.tittel == "Tittel ny dokument" }!!
            responseNyDokument.body!!.dokumentreferanse shouldBe nyDokument.dokumentreferanse
            nyDokument.språk shouldBe "EN"
            nyDokument.tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
            nyDokument.rekkefølgeIndeks shouldBe forsendelse.dokumenter.size - 1
            nyDokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            nyDokument.arkivsystem shouldBe DokumentArkivSystem.UKJENT

            stubUtils.Valider().bestillDokumentIkkeKalt(HOVEDDOKUMENT_DOKUMENTMAL)
        }
    }

    @Test
    fun `Skal opprette forsendelse og legge til nytt dokument med annen språk på opprettet forsendelse`() {
        val nyDokumentmal = DOKUMENTMAL_UTGÅENDE_2
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            dokumenter = listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_VEDLEGG_1,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    journalpostId = "JOARK-123123213",
                    dokumentreferanse = "123213"
                )
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelseId = response.body!!.forsendelseId!!

        val forsendelseMedEnDokument = testDataManager.hentForsendelse(forsendelseId)!!

        forsendelseMedEnDokument.dokumenter shouldHaveSize 1
        val dokument = forsendelseMedEnDokument.dokumenter[0]
        dokument.dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
        dokument.arkivsystem shouldBe DokumentArkivSystem.JOARK

        val opprettDokumentForespørsel = OpprettDokumentForespørsel(
            tittel = "Tittel ny dokument",
            dokumentmalId = nyDokumentmal,
            språk = "EN"
        )
        val responseNyDokument =
            utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(forsendelseId)!!

            forsendelse.dokumenter shouldHaveSize 2
            val nyDokument = forsendelse.dokumenter.find { it.tittel == "Tittel ny dokument" }!!
            nyDokument.språk shouldBe "EN"
            nyDokument.tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
            nyDokument.rekkefølgeIndeks shouldBe forsendelse.dokumenter.size - 1
            nyDokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            nyDokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER

            stubUtils.Valider().bestillDokumentKaltMed(
                nyDokumentmal,
                "\"dokumentreferanse\":\"${nyDokument.dokumentreferanse}\"",
                "\"språk\":\"${nyDokument.språk}\""
            )
        }
    }

    @Test
    fun `Skal opprette forsendelse uten dokument og legge til nytt dokument på opprettet forsendelse`() {
        val opprettForsendelseForespørsel =
            nyOpprettForsendelseForespørsel().copy(dokumenter = emptyList())

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelseId = response.body!!.forsendelseId!!

        val forsendelseMedEnDokument = testDataManager.hentForsendelse(forsendelseId)!!

        forsendelseMedEnDokument.dokumenter shouldHaveSize 0

        val opprettDokumentForespørsel = OpprettDokumentForespørsel(
            tittel = "Tittel ny dokument",
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
        )
        val responseNyDokument =
            utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(forsendelseId)!!

            forsendelse.dokumenter shouldHaveSize 1
            val nyDokument = forsendelse.dokumenter[0]
            nyDokument.tilknyttetSom shouldBe DokumentTilknyttetSom.HOVEDDOKUMENT
            nyDokument.rekkefølgeIndeks shouldBe 0
            nyDokument.dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            nyDokument.arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
            stubUtils.Valider().bestillDokumentKaltMed(HOVEDDOKUMENT_DOKUMENTMAL)
        }
    }

    @Test
    fun `Skal opprette forsendelse og fjerne dokument fra opprettet forsendelse`() {
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

        val responseNyDokument =
            utførSlettDokumentForespørsel(forsendelseId, dokumentSomSkalSlettes.dokumentreferanse)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        forsendelse.dokumenter shouldHaveSize 3
        val slettetDokument =
            forsendelse.dokumenter.find { it.dokumentId == dokumentSomSkalSlettes.dokumentId }!!
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

    @Test
    fun `Skal opprette forsendelse med dokumentadato`() {
        val dokumentdato = LocalDateTime.parse("2021-01-01T01:02:03")
        val opprettForsendelseForespørsel =
            nyOpprettForsendelseForespørsel().copy(
                dokumenter = listOf(
                    OpprettDokumentForespørsel(
                        tittel = TITTEL_HOVEDDOKUMENT,
                        dokumentmalId = DOKUMENTMAL_NOTAT,
                        dokumentDato = dokumentdato
                    )
                )
            )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)!!

        assertSoftly {
            forsendelse.dokumenter[0].dokumentDato shouldBeEqual dokumentdato
        }
    }
}
