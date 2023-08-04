package no.nav.bidrag.dokument.forsendelse.api

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
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
import java.time.ZoneOffset

class DistribuerKontrollerTest : KontrollerTestRunner() {

    @MockkBean
    lateinit var gcpCloudStorage: GcpCloudStorage
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
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT),
                    nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, dokumentStatus = DokumentStatus.FERDIGSTILT)
                )
            )
        )
        val response = utførHentKanDistribuere(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK
    }

    @Test
    fun `skal returnere at forsendelse ikke kan distribueres hvis forsendelse er inneholder dokumenter som ikke er ferdigstilt`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT
                    ),
                    nyttDokument(
                        journalpostId = "123213213",
                        dokumentreferanseOriginal = "123213213",
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        rekkefølgeIndeks = 1
                    )
                )
            )
        )

        val response = utførHentKanDistribuere(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.NOT_ACCEPTABLE
    }

    @Test
    fun `skal feile distribusjon hvis forsendelse er inneholder dokumenter som ikke er ferdigstilt`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        rekkefølgeIndeks = 0
                    ),
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        rekkefølgeIndeks = 1
                    ),
                    nyttDokument(
                        journalpostId = "13213123",
                        dokumentreferanseOriginal = "123213123213",
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        rekkefølgeIndeks = 2
                    )
                )
            )
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.headers["Warning"]!![0] shouldContain "Alle dokumenter er ikke ferdigstilt"
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

        val opprettetEpochMillis = forsendelse.opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)

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
                        "\"tittel\":\"Tittel på hoveddokument\"," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"tema\":\"FAR\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}_$opprettetEpochMillis\"," +
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
            val opprettetEpochMillis = forsendelse.opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"tittel\":\"Tittel på hoveddokument\"," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}\"," +
                        "\"journalførendeEnhet\":\"${forsendelse.enhet}_$opprettetEpochMillis\"" +
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
    fun `skal distribuere forsendelse med dokumenter knyttet til andre forsendelser`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelseOriginal = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        tittel = "Dokument som det knyttes til 1",
                        dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                        rekkefølgeIndeks = 0
                    ),
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        tittel = "Dokument som det knyttes til 2",
                        dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                        rekkefølgeIndeks = 1
                    )
                )
            )
        )
        val dokumentKnyttetTil1 = forsendelseOriginal.dokumenter[0]
        val dokumentKnyttetTil2 = forsendelseOriginal.dokumenter[1]
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = dokumentKnyttetTil1.forsendelseId.toString(),
                        dokumentreferanseOriginal = dokumentKnyttetTil1.dokumentreferanse,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        tittel = "Dokument knyttet til forsendelse 1",
                        dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                        rekkefølgeIndeks = 0
                    ),
                    nyttDokument(
                        journalpostId = dokumentKnyttetTil2.forsendelseId.toString(),
                        dokumentreferanseOriginal = dokumentKnyttetTil2.dokumentreferanse,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        tittel = "Dokument knyttet til forsendelse 2",
                        dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                        rekkefølgeIndeks = 1
                    )
                )
            )
        )
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
            val opprettetEpochMillis = forsendelse.opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"tittel\":\"Dokument knyttet til forsendelse 1\"," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Dokument knyttet til forsendelse 1\",\"brevkode\":\"$DOKUMENTMAL_UTGÅENDE\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Dokument knyttet til forsendelse 2\",\"brevkode\":\"$DOKUMENTMAL_UTGÅENDE\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}_$opprettetEpochMillis\"," +
                        "\"journalførendeEnhet\":\"${forsendelse.enhet}_$opprettetEpochMillis\"" +
                        "}"
            )
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
            stubUtils.Valider().hentDokumentKalt(forsendelseOriginal.forsendelseIdMedPrefix, forsendelseOriginal.dokumenter[0].dokumentreferanse)
            stubUtils.Valider().hentDokumentKalt(forsendelseOriginal.forsendelseIdMedPrefix, forsendelseOriginal.dokumenter[1].dokumentreferanse)
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
            val opprettetEpochMillis = forsendelse.opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"tittel\":\"Tittel på hoveddokument\"," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}_$opprettetEpochMillis\"," +
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
            val opprettetEpochMillis = forsendelse.opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)

            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"tittel\":\"Tittel på hoveddokument\"," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument (dokumentet er sendt per post med vedlegg)\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"kanal\":\"LOKAL_UTSKRIFT\"," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"BIF_${forsendelse.forsendelseId}_$opprettetEpochMillis\"," +
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
