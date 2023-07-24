package no.nav.bidrag.dokument.forsendelse.api

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentDetaljer
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRedigeringMetadataResponsDto
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseStatusTo
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.LagreFilResponse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.DokumentMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettDokumentMetadata
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class RedigerForsendelseKontrollerTest : KontrollerTestRunner() {

    @MockkBean
    lateinit var gcpCloudStorage: GcpCloudStorage

    @BeforeEach
    fun initMockCloudStorage() {

        every { gcpCloudStorage.lagreFil(any(), any()) } returns LagreFilResponse("2", "filsti")
        every { gcpCloudStorage.hentFil(any()) } returns "asdasd".toByteArray()
    }

    @Test
    fun `Skal hente dokument redigeringmetadata hvis ikke finnes fra før`() {
        val journalpostId = "123123213"
        val dokumentreferanse1 = "3123333213213"
        val dokumentreferanse2 = "44124214214"
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.UNDER_PRODUKSJON,
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = journalpostId,
                        dokumentreferanseOriginal = null,
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Tittel på dokument"
                    )
                )
            )
        )

        val metadataResponse = listOf(
            opprettDokumentMetadata(journalpostId, dokumentreferanse1),
            opprettDokumentMetadata(journalpostId, dokumentreferanse2).copy(tittel = "Tittel vedlegg")
        )
        stubUtils.stubHentDokumentFraPDF()
        stubUtils.stubHentDokumentMetadata("JOARK-$journalpostId", response = metadataResponse)
        val dokument = forsendelse.dokumenter[0]
        val response = utførHentRedigeringmetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)

        assertSoftly {
            response.statusCode shouldBe HttpStatus.OK
            val metadata = response.body!!
            metadata.redigeringMetadata shouldBe null
            metadata.forsendelseStatus shouldBe ForsendelseStatusTo.UNDER_PRODUKSJON
            metadata.status shouldBe DokumentStatusTo.MÅ_KONTROLLERES
            metadata.tittel shouldBe "Tittel på dokument"

            metadata.dokumenter shouldHaveSize 2
            metadata.dokumenter[0].tittel shouldBe metadataResponse[0].tittel
            metadata.dokumenter[0].antallSider shouldBe 3

            metadata.dokumenter[1].tittel shouldBe metadataResponse[1].tittel
            metadata.dokumenter[1].antallSider shouldBe 3

            stubUtils.Valider().hentDokumentKalt(metadataResponse[0].journalpostId!!, metadataResponse[0].dokumentreferanse!!, 1)
            stubUtils.Valider().hentDokumentKalt(metadataResponse[1].journalpostId!!, metadataResponse[1].dokumentreferanse!!, 1)
            stubUtils.Valider().hentDokumentMetadataKalt("JOARK-$journalpostId")
        }
    }

    @Test
    fun `Skal hente eksisterende dokument redigeringmetadata`() {
        val journalpostId = "123123213"
        val dokumentreferanse1 = "3123333213213"
        val dokumentreferanse2 = "44124214214"
        val redigeringmetadata = "redigeringdata"
        val metadataDo = DokumentMetadataDo()
        metadataDo.lagreRedigeringmetadata(redigeringmetadata)
        metadataDo.lagreDokumentDetaljer(
            listOf(
                DokumentDetaljer(tittel = "Hoveddok", dokumentreferanse = dokumentreferanse1, antallSider = 3),
                DokumentDetaljer(tittel = "Vedlegg", dokumentreferanse = dokumentreferanse2, antallSider = 8)
            )
        )
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = journalpostId,
                        dokumentreferanseOriginal = null,
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Tittel på dokument"
                    ).copy(metadata = metadataDo)
                )
            )
        )

        stubUtils.stubHentDokumentFraPDF()
        stubUtils.stubHentDokumentMetadata("JOARK-$journalpostId", response = emptyList())
        val dokument = forsendelse.dokumenter[0]
        val response = utførHentRedigeringmetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)

        assertSoftly {
            response.statusCode shouldBe HttpStatus.OK
            val metadata = response.body!!
            metadata.redigeringMetadata shouldBe redigeringmetadata
            metadata.forsendelseStatus shouldBe ForsendelseStatusTo.UNDER_PRODUKSJON
            metadata.status shouldBe DokumentStatusTo.MÅ_KONTROLLERES
            metadata.tittel shouldBe "Tittel på dokument"

            metadata.dokumenter shouldHaveSize 2
            metadata.dokumenter[0].tittel shouldBe metadataDo.hentDokumentDetaljer()?.get(0)?.tittel
            metadata.dokumenter[0].antallSider shouldBe metadataDo.hentDokumentDetaljer()?.get(0)?.antallSider

            metadata.dokumenter[1].tittel shouldBe metadataDo.hentDokumentDetaljer()?.get(1)?.tittel
            metadata.dokumenter[1].antallSider shouldBe metadataDo.hentDokumentDetaljer()?.get(1)?.antallSider

            stubUtils.Valider().hentDokumentIkkeKalt()
            stubUtils.Valider().hentDokumentMetadataKalt("JOARK-$journalpostId", antallGanger = 0)

        }
    }

    @Test
    fun `Skal opprette og hente dokument redigeringmetadata`() {
        val journalpostId = "123123213"
        val dokumentreferanse1 = "3123333213213"
        val dokumentreferanse2 = "44124214214"
        val redigeringmetadataNy = "redigeringmetedataNy"
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.UNDER_PRODUKSJON,
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = journalpostId,
                        dokumentreferanseOriginal = null,
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Tittel på dokument"
                    )
                )
            )
        )

        val metadataResponse = listOf(
            opprettDokumentMetadata(journalpostId, dokumentreferanse1),
            opprettDokumentMetadata(journalpostId, dokumentreferanse2).copy(tittel = "Tittel vedlegg")
        )
        stubUtils.stubHentDokumentFraPDF()
        stubUtils.stubHentDokumentMetadata("JOARK-$journalpostId", response = metadataResponse)
        val dokument = forsendelse.dokumenter[0]
        val response = utførHentRedigeringmetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)
        response.statusCode shouldBe HttpStatus.OK
        val responseLagre = utførLagreRedigeringmetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse, redigeringmetadataNy)
        responseLagre.statusCode shouldBe HttpStatus.OK
        val responseEtterLagre = utførHentRedigeringmetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)

        assertSoftly {
            val metadata = responseEtterLagre.body!!
            metadata.redigeringMetadata shouldBe redigeringmetadataNy
            metadata.forsendelseStatus shouldBe ForsendelseStatusTo.UNDER_PRODUKSJON
            metadata.status shouldBe DokumentStatusTo.MÅ_KONTROLLERES
            metadata.tittel shouldBe "Tittel på dokument"

            metadata.dokumenter shouldHaveSize 2
            metadata.dokumenter[0].tittel shouldBe metadataResponse[0].tittel
            metadata.dokumenter[0].antallSider shouldBe 3

            metadata.dokumenter[1].tittel shouldBe metadataResponse[1].tittel
            metadata.dokumenter[1].antallSider shouldBe 3

            stubUtils.Valider().hentDokumentKalt(metadataResponse[0].journalpostId!!, metadataResponse[0].dokumentreferanse!!, 1)
            stubUtils.Valider().hentDokumentKalt(metadataResponse[1].journalpostId!!, metadataResponse[1].dokumentreferanse!!, 1)
            stubUtils.Valider().hentDokumentMetadataKalt("JOARK-$journalpostId")
        }
    }

    @Test
    fun `Skal oppdatere redigeringmetadata`() {
        val journalpostId = "123123213"
        val dokumentreferanse1 = "3123333213213"
        val dokumentreferanse2 = "44124214214"
        val redigeringmetadata = "redigeringdata"
        val redigeringmetadataNy = "redigeringdataNy"
        val metadataDo = DokumentMetadataDo()
        metadataDo.lagreRedigeringmetadata(redigeringmetadata)
        metadataDo.lagreDokumentDetaljer(
            listOf(
                DokumentDetaljer(tittel = "Hoveddok", dokumentreferanse = dokumentreferanse1, antallSider = 3),
                DokumentDetaljer(tittel = "Vedlegg", dokumentreferanse = dokumentreferanse2, antallSider = 8)
            )
        )
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = journalpostId,
                        dokumentreferanseOriginal = null,
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Tittel på dokument"
                    ).copy(metadata = metadataDo)
                )
            )
        )

        val dokument = forsendelse.dokumenter[0]
        val responseLagre = utførLagreRedigeringmetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse, redigeringmetadataNy)
        responseLagre.statusCode shouldBe HttpStatus.OK

        val response = utførHentRedigeringmetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)

        assertSoftly {
            response.statusCode shouldBe HttpStatus.OK
            val metadata = response.body!!
            metadata.redigeringMetadata shouldBe redigeringmetadataNy
            metadata.forsendelseStatus shouldBe ForsendelseStatusTo.UNDER_PRODUKSJON
            metadata.status shouldBe DokumentStatusTo.MÅ_KONTROLLERES
            metadata.tittel shouldBe "Tittel på dokument"
        }
    }

    fun utførLagreRedigeringmetadata(
        forsendelseId: String,
        dokumentreferanse: String,
        nyMetadata: String
    ): ResponseEntity<DokumentRedigeringMetadataResponsDto> {
        return httpHeaderTestRestTemplate.patchForEntity<DokumentRedigeringMetadataResponsDto>(
            "${rootUri()}/redigering/$forsendelseId/$dokumentreferanse",
            HttpEntity(nyMetadata)
        )
    }

    fun utførHentRedigeringmetadata(
        forsendelseId: String,
        dokumentreferanse: String,
    ): ResponseEntity<DokumentRedigeringMetadataResponsDto> {
        return httpHeaderTestRestTemplate.getForEntity<DokumentRedigeringMetadataResponsDto>(
            "${rootUri()}/redigering/$forsendelseId/$dokumentreferanse",
        )
    }
}