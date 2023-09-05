package no.nav.bidrag.dokument.forsendelse.api

import com.ninjasquad.springmockk.SpykBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Ordering
import io.mockk.verify
import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.forsendelse.api.dto.BehandlingInfoDto
import no.nav.bidrag.dokument.forsendelse.api.dto.FerdigstillDokumentRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseIkkeDistribuertResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.opprettReferanseId
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.Duration
import java.time.LocalDateTime

class ForsendelsePersistensIT : KontrollerTestContainerRunner() {

    @SpykBean
    lateinit var gcpCloudStorage: GcpCloudStorage

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
    fun `Skal opprette forsendelse med behandlinginfo`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(
            behandlingInfo = BehandlingInfoDto(
                soknadId = "123123",
                vedtakId = "12321333",
                behandlingId = "2342323",
                erVedtakIkkeTilbakekreving = true,
                erFattetBeregnet = true,
                behandlingType = "BEHANDLING_TYPE",
                engangsBelopType = EngangsbelopType.TILBAKEKREVING,
                soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                soknadType = "EGET_TILTAK",
                stonadType = StonadType.FORSKUDD,
                vedtakType = VedtakType.FASTSETTELSE,
                barnIBehandling = listOf("13231231312", "43124324234")
            )
        )

        val response = utførOpprettForsendelseForespørsel(opprettForsendelseForespørsel)
        response.statusCode shouldBe HttpStatus.OK

        await.atMost(Duration.ofSeconds(2)).untilAsserted {
            val forsendelse = testDataManager.hentForsendelse(response.body?.forsendelseId!!)
            forsendelse shouldNotBe null
            forsendelse!!.tema shouldBe ForsendelseTema.BID
            val behandlingInfo = forsendelse.behandlingInfo
            behandlingInfo shouldNotBe null
            behandlingInfo!!.behandlingType shouldBe null
            behandlingInfo.soknadId shouldBe "123123"
            behandlingInfo.vedtakId shouldBe "12321333"
            behandlingInfo.behandlingId shouldBe "2342323"
            behandlingInfo.erVedtakIkkeTilbakekreving shouldBe true
            behandlingInfo.erFattetBeregnet shouldBe true
            behandlingInfo.engangsBelopType shouldBe EngangsbelopType.TILBAKEKREVING
            behandlingInfo.soknadFra shouldBe SoknadFra.BIDRAGSMOTTAKER
            behandlingInfo.soknadType shouldBe "EGET_TILTAK"
            behandlingInfo.stonadType shouldBe StonadType.FORSKUDD
            behandlingInfo.vedtakType shouldBe VedtakType.FASTSETTELSE
            behandlingInfo.barnIBehandling shouldContain "13231231312"
            behandlingInfo.barnIBehandling shouldContain "43124324234"
        }

        val forsendelseResponse = utførHentForsendelse(response.body!!.forsendelseId.toString())
        val journalpost = forsendelseResponse.body!!

        journalpost.behandlingInfo shouldNotBe null
        journalpost.behandlingInfo!!.soknadId shouldBe "123123"
        journalpost.behandlingInfo!!.barnIBehandling!! shouldContainAnyOf listOf("13231231312", "43124324234")
    }

    @Test
    fun `Skal opprette forsendelse med tema FAR`() {
        val opprettForsendelseForespørsel = nyOpprettForsendelseForespørsel().copy(tema = JournalTema.FAR)

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

    @Test
    fun `Skal hente liste over forsendelser ikke distribuert`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    )
                )
            )
        )
        val forsendelse2 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    )
                )
            )
        )
        val forsendelse3 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    )
                )
            )
        )
        testDataManager.lagreForsendelse(forsendelse.copy(opprettetTidspunkt = LocalDateTime.now().minusDays(1)))
        testDataManager.lagreForsendelse(forsendelse2.copy(opprettetTidspunkt = LocalDateTime.now().minusDays(1)))
        val response: ResponseEntity<List<ForsendelseIkkeDistribuertResponsTo>> =
            httpHeaderTestRestTemplate.getForEntity("${rootUri()}/journal/ikkedistribuert")

        response.statusCode shouldBe HttpStatus.OK

        val journalpostListe = response.body!!
        assertSoftly {
            journalpostListe shouldHaveSize 2
            val forsendelseResponse1 = journalpostListe[0]
            forsendelseResponse1.forsendelseId shouldBe forsendelse.forsendelseIdMedPrefix
            forsendelseResponse1.saksnummer shouldBe forsendelse.saksnummer
            forsendelseResponse1.enhet shouldBe forsendelse.enhet
            forsendelseResponse1.tittel shouldBe forsendelse.dokumenter.hoveddokument!!.tittel
        }
    }

    @Test
    @Transactional
    fun `skal distribuere forsendelse med kontrollerte og redigerte dokumenter`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        val dokumentRedigeringData = "redigeringdata"
        val dokumentInnholdRedigering = "redigeringdata_PDF"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                tittel = "Tittel på forsendelse",
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        rekkefølgeIndeks = 0
                    ),
                    nyttDokument(
                        journalpostId = "45454545",
                        dokumentreferanseOriginal = "23123123",
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Tittel vedlegg må kontrolleres",
                        dokumentMalId = "BI100",
                        rekkefølgeIndeks = 1
                    ),
                    nyttDokument(
                        journalpostId = "545454",
                        dokumentreferanseOriginal = "45454545",
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Tittel vedlegg må kontrolleres 2",
                        dokumentMalId = "BI100",
                        rekkefølgeIndeks = 2
                    )
                )
            )
        )
        val dokument1 = forsendelse.dokumenter[1]
        val responseFerdigstill = utførFerdigstillDokument(
            forsendelse.forsendelseIdMedPrefix,
            dokument1.dokumentreferanse,
            request = FerdigstillDokumentRequest(
                fysiskDokument = dokumentInnholdRedigering.toByteArray(),
                redigeringMetadata = dokumentRedigeringData
            )
        )
        responseFerdigstill.statusCode shouldBe HttpStatus.OK

        val dokument2 = forsendelse.dokumenter[2]
        val responseFerdigstill2 = utførFerdigstillDokument(
            forsendelse.forsendelseIdMedPrefix,
            dokument2.dokumentreferanse,
            request = FerdigstillDokumentRequest(
                fysiskDokument = dokumentInnholdRedigering.toByteArray(),
                redigeringMetadata = dokumentRedigeringData
            )
        )
        responseFerdigstill2.statusCode shouldBe HttpStatus.OK

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
            val referanseId = oppdatertForsendelse.opprettReferanseId()
            oppdatertForsendelse.referanseId shouldBe referanseId
            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"tittel\":\"Tittel på hoveddokument\"," +
                        "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"dokumentreferanse\":\"${forsendelse.dokumenter[0].dokumentreferanse}\"}," +
                        "{\"tittel\":\"Tittel vedlegg må kontrolleres\",\"brevkode\":\"BI100\",\"dokumentreferanse\":\"${forsendelse.dokumenter[1].dokumentreferanse}\"}," +
                        "{\"tittel\":\"Tittel vedlegg må kontrolleres 2\",\"brevkode\":\"BI100\",\"dokumentreferanse\":\"${forsendelse.dokumenter[2].dokumentreferanse}\"}]," +
                        "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"$referanseId\"," +
                        "\"journalførendeEnhet\":\"${forsendelse.enhet}\"" +
                        "}"
            )
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
            verify(ordering = Ordering.ORDERED) {
                gcpCloudStorage.slettFil(eq("dokumenter/${forsendelse.dokumenter.vedlegger[0].filsti}"))
                gcpCloudStorage.slettFil(eq("dokumenter/${forsendelse.dokumenter.vedlegger[1].filsti}"))
            }
        }
    }
}
