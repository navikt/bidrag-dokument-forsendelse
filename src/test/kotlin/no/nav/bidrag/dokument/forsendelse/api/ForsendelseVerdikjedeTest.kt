package no.nav.bidrag.dokument.forsendelse.api

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.forsendelse.api.dto.BehandlingInfoDto
import no.nav.bidrag.dokument.forsendelse.api.dto.FerdigstillDokumentRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.hendelse.DokumentHendelseLytter
import no.nav.bidrag.dokument.forsendelse.hendelse.JournalpostKafkaHendelseProdusent
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.opprettReferanseId
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.jsonToString
import no.nav.bidrag.dokument.forsendelse.utils.nyOpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.utils.opprettHendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class ForsendelseVerdikjedeTest : KontrollerTestContainerRunner() {

    @Autowired
    lateinit var dokumentHendelseLytter: DokumentHendelseLytter

    @MockkBean
    lateinit var journalpostKafkaHendelseProdusent: JournalpostKafkaHendelseProdusent

    @BeforeEach
    fun initHendelseMock() {
        every { journalpostKafkaHendelseProdusent.publiserForsendelse(any()) } returns Unit
        every { journalpostKafkaHendelseProdusent.publiser(any()) } returns Unit
    }

    @Test
    fun `Full verdikjede test - skal opprette forsendelse med lenket dokumenter og bestille distribusjon`() {
        val dokumentRedigeringData = "REDIGERINGDATA"
        val dokumentInnholdRedigering = "REDIGERINGDATA_BYTE"
        val originalForsendelseResponse = utførOpprettForsendelseForespørsel(
            nyOpprettForsendelseForespørsel().copy(
                behandlingInfo = BehandlingInfoDto(
                    soknadId = "123213",
                    erFattetBeregnet = true,
                    soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                    stonadType = StonadType.FORSKUDD,
                    vedtakType = VedtakType.FASTSETTELSE
                ),
                dokumenter = listOf(
                    OpprettDokumentForespørsel(
                        tittel = TITTEL_HOVEDDOKUMENT,
                        dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
                    ),
                    OpprettDokumentForespørsel(
                        tittel = TITTEL_HOVEDDOKUMENT,
                        journalpostId = "JOARK-64443434",
                        dokumentreferanse = "12313213"

                    )
                )
            )
        )
        originalForsendelseResponse.statusCode shouldBe HttpStatus.OK
        val originalForsendelse = testDataManager.hentForsendelse(originalForsendelseResponse.body!!.forsendelseId!!)!!

        val originalDokument = originalForsendelse.dokumenter.sortertEtterRekkefølge[0]
        val originalForsendelseId = originalForsendelse.forsendelseId!!
        val forsendelse2Respons = utførOpprettForsendelseForespørsel(
            nyOpprettForsendelseForespørsel().copy(
                dokumenter = listOf(
                    OpprettDokumentForespørsel(
                        tittel = TITTEL_HOVEDDOKUMENT,
                        dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
                    )
                )
            )
        )
        val forsendelse2 = testDataManager.hentForsendelse(forsendelse2Respons.body!!.forsendelseId!!)!!

        // Opprett ny forsendelse som skal distribueres
        val forsendelseOpprettetResponse = utførOpprettForsendelseForespørsel(
            nyOpprettForsendelseForespørsel().copy(
                behandlingInfo = BehandlingInfoDto(
                    soknadId = "123213",
                    erFattetBeregnet = true,
                    soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                    stonadType = StonadType.FORSKUDD,
                    vedtakType = VedtakType.FASTSETTELSE
                ),
                dokumenter = listOf(
                    OpprettDokumentForespørsel(
                        tittel = TITTEL_HOVEDDOKUMENT,
                        dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
                    )
                )
            )
        )

        forsendelseOpprettetResponse.statusCode shouldBe HttpStatus.OK
        val forsendelseIdSomSkalDistribueres = forsendelseOpprettetResponse.body!!.forsendelseId!!

        // Legg til dokument som kobling til original forsendelse
        val kobleTilOriginalForsendelseDokumentResponse = utførOppdaterForsendelseForespørsel(
            forsendelse2.forsendelseId.toString(),
            OppdaterForsendelseForespørsel(
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        dokumentreferanse = forsendelse2.dokumenter.sortertEtterRekkefølge[0].dokumentreferanse
                    ),
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel koblet dokument fra original",
                        dokumentreferanse = originalDokument.dokumentreferanse,
                        journalpostId = "BIF-${originalForsendelse.forsendelseId}"
                    ),
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel dokument fra Joark",
                        dokumentreferanse = originalForsendelse.dokumenter.sortertEtterRekkefølge[1].dokumentreferanse,
                        journalpostId = "JOARK-${originalForsendelse.forsendelseId}"
                    )
                )
            )
        )

        kobleTilOriginalForsendelseDokumentResponse.statusCode shouldBe HttpStatus.OK

        val forsendelse2Oppdatert = testDataManager.hentForsendelse(forsendelse2.forsendelseId!!)!!
        val opprettetForsendelse = testDataManager.hentForsendelse(forsendelseOpprettetResponse.body?.forsendelseId!!)!!

        // Koble dokument fra forsendelse 2 til opprettet forsendelse
        val oppdaterNyForsendelseResponse = utførOppdaterForsendelseForespørsel(
            forsendelseIdSomSkalDistribueres.toString(),
            OppdaterForsendelseForespørsel(
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        dokumentreferanse = opprettetForsendelse.dokumenter.sortertEtterRekkefølge[0].dokumentreferanse
                    ),
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel koblet dokument fra original",
                        dokumentreferanse = forsendelse2Oppdatert.dokumenter.sortertEtterRekkefølge[1].dokumentreferanse,
                        journalpostId = "BIF-${forsendelse2Oppdatert.forsendelseId}"
                    ),
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel dokument fra Joark",
                        dokumentreferanse = "123213213213",
                        journalpostId = "JOARK-123123123123"
                    )
                )
            )
        )

        oppdaterNyForsendelseResponse.statusCode shouldBe HttpStatus.OK
        val nyForsendelseDokumentreferanse = oppdaterNyForsendelseResponse.body!!.dokumenter[0].dokumentreferanse
        val nyForsendelseDokumentreferanseMåKontrolleres = oppdaterNyForsendelseResponse.body!!.dokumenter[2].dokumentreferanse

        dokumentHendelseLytter.prossesserDokumentHendelse(
            opprettConsumerRecord(
                originalDokument.dokumentreferanse,
                jsonToString(opprettHendelse(originalDokument.dokumentreferanse, status = DokumentStatusDto.UNDER_REDIGERING))
            )
        )
        dokumentHendelseLytter.prossesserDokumentHendelse(
            opprettConsumerRecord(
                nyForsendelseDokumentreferanse,
                jsonToString(opprettHendelse(nyForsendelseDokumentreferanse, status = DokumentStatusDto.UNDER_REDIGERING))
            )
        )

        assertSoftly("Valider forsendelse") {
            val opprettetForsendelseOppdatert = testDataManager.hentForsendelse(forsendelseIdSomSkalDistribueres)!!
            opprettetForsendelseOppdatert.dokumenter shouldHaveSize 3
            val dokumenter = opprettetForsendelseOppdatert.dokumenter.sortertEtterRekkefølge
            dokumenter[0].dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING
            dokumenter[1].dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING
            dokumenter[2].dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
        }

        dokumentHendelseLytter.prossesserDokumentHendelse(
            opprettConsumerRecord(
                originalDokument.dokumentreferanse,
                jsonToString(opprettHendelse(originalDokument.dokumentreferanse, status = DokumentStatusDto.FERDIGSTILT))
            )
        )
        dokumentHendelseLytter.prossesserDokumentHendelse(
            opprettConsumerRecord(
                nyForsendelseDokumentreferanse,
                jsonToString(opprettHendelse(nyForsendelseDokumentreferanse, status = DokumentStatusDto.FERDIGSTILT))
            )
        )
        utførFerdigstillDokument(
            forsendelseIdSomSkalDistribueres.toString(),
            nyForsendelseDokumentreferanseMåKontrolleres,
            request = FerdigstillDokumentRequest(
                fysiskDokument = dokumentInnholdRedigering.toByteArray(),
                redigeringMetadata = dokumentRedigeringData
            )
        )
        assertSoftly("Valider forsendelse etter hendelse") {
            val opprettetForsendelseOppdatert = testDataManager.hentForsendelse(forsendelseIdSomSkalDistribueres)!!
            opprettetForsendelseOppdatert.dokumenter shouldHaveSize 3
            val dokumenter = opprettetForsendelseOppdatert.dokumenter.sortertEtterRekkefølge
            dokumenter[0].dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokumenter[0].metadata.hentProdusertTidspunkt()!! shouldHaveSameDayAs LocalDateTime.now()
            dokumenter[1].dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokumenter[1].metadata.hentProdusertTidspunkt()!! shouldHaveSameDayAs LocalDateTime.now()
            dokumenter[2].dokumentStatus shouldBe DokumentStatus.KONTROLLERT

            val originalForsendelseOppdatert = testDataManager.hentForsendelse(originalForsendelseId)!!
            originalForsendelseOppdatert.dokumenter shouldHaveSize 2
            val dokumenter2 = originalForsendelseOppdatert.dokumenter.sortertEtterRekkefølge
            dokumenter2[0].dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokumenter2[0].metadata.hentProdusertTidspunkt()!! shouldHaveSameDayAs LocalDateTime.now()
            dokumenter2[1].dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES

            val forsendelse2OppdatertEtterHendelse = testDataManager.hentForsendelse(forsendelse2?.forsendelseId!!)!!
            forsendelse2OppdatertEtterHendelse.dokumenter shouldHaveSize 3
            val dokumenter3 = forsendelse2OppdatertEtterHendelse.dokumenter.sortertEtterRekkefølge
            dokumenter3[0].dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            dokumenter3[1].dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            dokumenter3[1].metadata.hentProdusertTidspunkt()!! shouldHaveSameDayAs LocalDateTime.now()
            dokumenter3[2].dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
        }

        val bestillingId = "adsasdasdasdas"
        val nyJournalpostId = "JOARK-123213123123"
        val opprettetForsendelseOppdatert = testDataManager.hentForsendelse(forsendelseIdSomSkalDistribueres)!!

        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId, nyJournalpostId)
        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            opprettetForsendelseOppdatert.dokumenter.sortertEtterRekkefølge.map {
                OpprettDokumentDto(
                    it.tittel,
                    dokumentreferanse = "JOARK${it.dokumentreferanse}"
                )
            }
        )

        val dokumenter = opprettetForsendelseOppdatert.dokumenter.sortertEtterRekkefølge

        // Endre rekkefølge på dokumenter og oppdater forsendelse tittel
        utførOppdaterForsendelseForespørsel(
            forsendelseIdSomSkalDistribueres.toString(),
            OppdaterForsendelseForespørsel(
                tittel = "Tittel på forsendelse",
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        dokumentreferanse = dokumenter[2].dokumentreferanse
                    ),
                    OppdaterDokumentForespørsel(
                        dokumentreferanse = dokumenter[0].dokumentreferanse
                    ),
                    OppdaterDokumentForespørsel(
                        dokumentreferanse = dokumenter[1].dokumentreferanse
                    )
                )
            )
        )

        val distribuerResponse = utførDistribuerForsendelse(forsendelseIdSomSkalDistribueres.toString())
        distribuerResponse.statusCode shouldBe HttpStatus.OK
        distribuerResponse.body!!.journalpostId shouldBe nyJournalpostId

        assertSoftly("Skal validere forsendelse etter distribusjon") {
            val forsendelseEtterDistribusjon = testDataManager.hentForsendelse(forsendelseIdSomSkalDistribueres)!!
            val referanseId = forsendelseEtterDistribusjon.opprettReferanseId()
            forsendelseEtterDistribusjon.referanseId shouldBe referanseId
            val forsendelseResponseEtterDistribusjon = utførHentForsendelse(forsendelseIdSomSkalDistribueres.toString())
            forsendelseResponseEtterDistribusjon.statusCode shouldBe HttpStatus.OK
            val responseBody = forsendelseResponseEtterDistribusjon.body!!
            responseBody.status shouldBe ForsendelseStatusTo.DISTRIBUERT
            stubUtils.Valider().opprettJournalpostKaltMed(
                "{" +
                        "\"skalFerdigstilles\":true," +
                        "\"tittel\":\"Tittel på forsendelse\"," +
                        "\"gjelderIdent\":\"${opprettetForsendelseOppdatert.gjelderIdent}\"," +
                        "\"avsenderMottaker\":{\"navn\":\"${opprettetForsendelseOppdatert.mottaker?.navn}\",\"ident\":\"${opprettetForsendelseOppdatert.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                        "\"dokumenter\":[" +
                        "{\"tittel\":\"Ny tittel dokument fra Joark\",\"fysiskDokument\":\"UkVESUdFUklOR0RBVEFfQllURQ==\"}," +
                        "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                        "{\"tittel\":\"Ny tittel koblet dokument fra original\",\"brevkode\":\"$DOKUMENTMAL_UTGÅENDE\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                        "\"tilknyttSaker\":[\"${opprettetForsendelseOppdatert.saksnummer}\"]," +
                        "\"tema\":\"BID\"," +
                        "\"journalposttype\":\"UTGÅENDE\"," +
                        "\"referanseId\":\"$referanseId\"," +
                        "\"journalførendeEnhet\":\"${opprettetForsendelseOppdatert.enhet}\"" +
                        "}"
            )
        }
    }

    fun opprettConsumerRecord(key: String, value: String) = ConsumerRecord("", 1, 1L, key, value)
}
