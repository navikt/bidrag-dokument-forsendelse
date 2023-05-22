package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.med
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

class DistribuerKontrollerTest : KontrollerTestRunner() {
    protected fun utførHentKanDistribuere(forsendelseId: String): ResponseEntity<Unit> {
        return httpHeaderTestRestTemplate.getForEntity<Unit>("${rootUri()}/journal/distribuer/$forsendelseId/enabled")
    }

    protected fun utførDistribuerForsendelse(
        forsendelseId: String,
        forespørsel: DistribuerJournalpostRequest? = null,
        batchId: String? = null
    ): ResponseEntity<DistribuerJournalpostResponse> {
        return httpHeaderTestRestTemplate.postForEntity<DistribuerJournalpostResponse>(
            "${rootUri()}/journal/distribuer/$forsendelseId${batchId?.let { "?batchId=$it" }}",
            forespørsel?.let { HttpEntity(it) }
        )
    }

    @Test
    fun `skal returnere at forsendelse kan distribueres hvis forsendelse er ferdigstilt`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT)
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, dokumentStatus = DokumentStatus.FERDIGSTILT)
        }

        val response = utførHentKanDistribuere(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK
    }

    @Test
    fun `skal returnere at forsendelse ikke kan distribueres hvis forsendelse er inneholder dokumenter som ikke er ferdigstilt`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT)
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }

        val response = utførHentKanDistribuere(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.NOT_ACCEPTABLE
    }

    @Test
    fun `skal ikke distribuere forsendelse hvis et av dokumentene har tittel med ugyldig tegn`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
            +nyttDokument(
                journalpostId = null,
                dokumentreferanseOriginal = null,
                dokumentStatus = DokumentStatus.FERDIGSTILT,
                tittel = "\u001A Tittel test \u001A",
                dokumentMalId = "BI100",
                rekkefølgeIndeks = 1
            )
        }

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.BAD_REQUEST

        response.headers["Warning"]?.get(0) shouldBe "Forsendelsen kan ikke ferdigstilles: Dokument med tittel   Tittel test   og dokumentreferanse ${forsendelse.dokumenter[1].dokumentreferanse} i forsendelse ${forsendelse.forsendelseId} inneholder ugyldig tegn"
    }

    @Test
    fun `skal distribuere forsendelse med tema FAR`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            med tema ForsendelseTema.FAR
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
            +nyttDokument(
                journalpostId = null,
                dokumentreferanseOriginal = null,
                dokumentStatus = DokumentStatus.FERDIGSTILT,
                tittel = "Tittel vedlegg",
                dokumentMalId = "BI100",
                rekkefølgeIndeks = 1
            )
        }

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") }
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"tema\":\"FAR\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}\"," +
                        "\"journalførendeEnhet\":\"${forsendelse.enhet}\"" +
                        "}"
            )
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
            stubUtils.Valider().hentDokumentKalt(forsendelse.forsendelseIdMedPrefix, forsendelse.dokumenter.vedlegger[0].dokumentreferanse)
            stubUtils.Valider()
                .hentDokumentKalt(
                    forsendelse.dokumenter.hoveddokument?.journalpostId!!,
                    forsendelse.dokumenter.hoveddokument?.dokumentreferanseOriginal!!
                )
        }
    }

    @Test
    fun `skal distribuere forsendelse`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
            +nyttDokument(
                journalpostId = null,
                dokumentreferanseOriginal = null,
                dokumentStatus = DokumentStatus.FERDIGSTILT,
                tittel = "Tittel vedlegg",
                dokumentMalId = "BI100",
                rekkefølgeIndeks = 1
            )
        }

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") }
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}\"," +
                        "\"journalførendeEnhet\":\"${forsendelse.enhet}\"" +
                        "}"
            )
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
            stubUtils.Valider().hentDokumentKalt(forsendelse.forsendelseIdMedPrefix, forsendelse.dokumenter.vedlegger[0].dokumentreferanse)
            stubUtils.Valider()
                .hentDokumentKalt(
                    forsendelse.dokumenter.hoveddokument?.journalpostId!!,
                    forsendelse.dokumenter.hoveddokument?.dokumentreferanseOriginal!!
                )
        }
    }

    @Test
    fun `skal ikke distribuere hvis allerede distribuert`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.DISTRIBUERT,
                arkivJournalpostId = nyJournalpostId,
                distribusjonBestillingsId = bestillingId,
                dokumenter = listOf(nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0))
            )
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        assertSoftly {
            response.body!!.journalpostId shouldBe nyJournalpostId
            response.body!!.bestillingsId shouldBe bestillingId
            stubUtils.Valider().opprettJournalpostIkkeKalt()
            stubUtils.Valider().bestillDokumentIkkeKalt(HOVEDDOKUMENT_DOKUMENTMAL)
        }
    }

    @Test
    fun `skal distribuere forsendelse med batchId`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        val batchId = "FB050"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
            +nyttDokument(
                journalpostId = null,
                dokumentreferanseOriginal = null,
                dokumentStatus = DokumentStatus.FERDIGSTILT,
                tittel = "Tittel vedlegg",
                dokumentMalId = "BI100",
                rekkefølgeIndeks = 1
            )
        }

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") }
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix, batchId = batchId)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.batchId shouldBe batchId
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}\"," +
                        "\"journalførendeEnhet\":\"${forsendelse.enhet}\"" +
                        "}"
            )
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId", batchId = batchId)
            stubUtils.Valider().hentDokumentKalt(forsendelse.forsendelseIdMedPrefix, forsendelse.dokumenter.vedlegger[0].dokumentreferanse)
            stubUtils.Valider()
                .hentDokumentKalt(
                    forsendelse.dokumenter.hoveddokument?.journalpostId!!,
                    forsendelse.dokumenter.hoveddokument?.dokumentreferanseOriginal!!
                )
        }
    }

    @Test
    fun `skal distribuere forsendelse lokalt`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0),
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        tittel = "Tittel vedlegg",
                        dokumentMalId = "BI100",
                        rekkefølgeIndeks = 1
                    )
                )
            )
        )

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") }
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix, DistribuerJournalpostRequest(lokalUtskrift = true))

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe null
            oppdatertForsendelse.distribusjonKanal shouldBe DistribusjonKanal.LOKAL_UTSKRIFT
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT_LOKALT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"kanal\":\"LOKAL_UTSKRIFT\"," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}\"," +
                        "\"journalførendeEnhet\":\"${forsendelse.enhet}\"" +
                        "}"
            )
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId", "\"lokalUtskrift\":true")
            stubUtils.Valider().hentDokumentKalt(forsendelse.forsendelseIdMedPrefix, forsendelse.dokumenter.vedlegger[0].dokumentreferanse)
            stubUtils.Valider()
                .hentDokumentKalt(
                    forsendelse.dokumenter.hoveddokument?.journalpostId!!,
                    forsendelse.dokumenter.hoveddokument?.dokumentreferanseOriginal!!
                )
        }
    }
}
