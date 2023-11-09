package no.nav.bidrag.dokument.forsendelse.api

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.DokumentMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_STATISK_VEDLEGG
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENT_FIL
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import no.nav.bidrag.transport.dokument.DokumentFormatDto
import no.nav.bidrag.transport.dokument.DokumentStatusDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class DokumentKontrollerTest : KontrollerTestRunner() {

    @MockkBean
    lateinit var gcpCloudStorage: GcpCloudStorage

    @BeforeEach
    fun initGcpMock() {
        every { gcpCloudStorage.hentFil(any()) } returns DOKUMENT_FIL.toByteArray()
    }

    @Test
    fun `skal hente dokumentmetadata`() {
        val dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")

        val originalForsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = dokumentDato
                    )
                )
            )
        )
        val originalDokument = originalForsendelse.dokumenter[0]
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = dokumentDato
                    ),
                    nyttDokument(
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        dokumentreferanseOriginal = "123213123",
                        journalpostId = "123123213123",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentDato = dokumentDato
                    ),
                    nyttDokument(
                        journalpostId = originalForsendelse.forsendelseId.toString(),
                        dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        dokumentDato = dokumentDato
                    )
                )
            )
        )

        val dokumenter = forsendelse.dokumenter.sortertEtterRekkefølge
        val response = utførHentDokumentMetadata(forsendelse.forsendelseId.toString())

        assertSoftly {
            response.statusCode shouldBe HttpStatus.OK
            val resBody = response.body!!
            resBody shouldHaveSize 3
        }

        assertSoftly("Valider dokument 1") {
            val metadata = response.body?.get(0)!!
            metadata.journalpostId shouldBe null
            metadata.dokumentreferanse shouldBe dokumenter[0].dokumentreferanse
            metadata.format shouldBe DokumentFormatDto.MBDOK
            metadata.arkivsystem shouldBe DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
        }

        assertSoftly("Valider dokument 2") {
            val metadata = response.body?.get(1)!!
            metadata.journalpostId shouldBe "JOARK-${dokumenter[1].journalpostIdOriginal}"
            metadata.dokumentreferanse shouldBe dokumenter[1].dokumentreferanseOriginal
            metadata.format shouldBe DokumentFormatDto.PDF
            metadata.status shouldBe DokumentStatusDto.UNDER_REDIGERING
            metadata.arkivsystem shouldBe DokumentArkivSystemDto.JOARK
        }

        assertSoftly("Valider dokument 3") {
            val metadata = response.body?.get(2)!!
            metadata.journalpostId shouldBe null
            metadata.dokumentreferanse shouldBe originalDokument.dokumentreferanse
            metadata.format shouldBe DokumentFormatDto.MBDOK
            metadata.status shouldBe DokumentStatusDto.UNDER_REDIGERING
            metadata.arkivsystem shouldBe DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
        }
    }

    @Test
    fun `skal ikke hente dokumentmetadata for slettet dokument`() {
        val dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")

        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = dokumentDato
                    ),
                    nyttDokument(
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        dokumentreferanseOriginal = "123213123",
                        journalpostId = "123123213123",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentDato = dokumentDato
                    ),
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        slettet = true,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        dokumentDato = dokumentDato
                    )
                )
            )
        )

        val dokumenter = forsendelse.dokumenter.sortertEtterRekkefølge
        val response = utførHentDokumentMetadata(forsendelse.forsendelseId.toString())

        assertSoftly {
            response.statusCode shouldBe HttpStatus.OK
            val resBody = response.body!!
            resBody shouldHaveSize 2
        }

        assertSoftly("Valider dokument 1") {
            val metadata = response.body?.get(0)!!
            metadata.journalpostId shouldBe null
            metadata.dokumentreferanse shouldBe dokumenter[0].dokumentreferanse
        }

        assertSoftly("Valider dokument 2") {
            val metadata = response.body?.get(1)!!
            metadata.journalpostId shouldBe "JOARK-${dokumenter[1].journalpostIdOriginal}"
            metadata.dokumentreferanse shouldBe dokumenter[1].dokumentreferanseOriginal
        }
    }

    @Test
    fun `skal hente dokumentmetadata enkel dokument med status under produksjon`() {
        val dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")

        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                        dokumentDato = dokumentDato
                    ),
                    nyttDokument(
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        dokumentreferanseOriginal = "123213123",
                        journalpostId = "123123213123",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentDato = dokumentDato
                    )
                )
            )
        )

        val dokumenter = forsendelse.dokumenter.sortertEtterRekkefølge
        val response = utførHentDokumentMetadata(forsendelse.forsendelseId.toString(), dokumenter[0].dokumentreferanse)

        assertSoftly {
            response.statusCode shouldBe HttpStatus.OK
            val resBody = response.body!!
            resBody shouldHaveSize 1
        }

        assertSoftly("Valider dokument 1") {
            val metadata = response.body?.get(0)!!
            metadata.journalpostId shouldBe null
            metadata.dokumentreferanse shouldBe dokumenter[0].dokumentreferanse
            metadata.status shouldBe DokumentStatusDto.UNDER_PRODUKSJON
        }
    }

    @Test
    fun `skal hente dokumentmetadata enkel dokument som har lenke til dokument i annen forsendelse`() {
        val dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
        val originalForsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = dokumentDato
                    )
                )
            )
        )
        val originalDokument = originalForsendelse.dokumenter[0]
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                        dokumentDato = dokumentDato
                    ),
                    nyttDokument(
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                        journalpostId = originalForsendelse.forsendelseId.toString(),
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        dokumentDato = dokumentDato
                    )
                )
            )
        )

        val dokumenter = forsendelse.dokumenter.sortertEtterRekkefølge

        assertSoftly("Valider metatadata for forsendelse") {
            val response = utførHentDokumentMetadata(forsendelse.forsendelseId.toString(), dokumenter[1].dokumentreferanse)
            response.statusCode shouldBe HttpStatus.OK
            val resBody = response.body!!
            resBody shouldHaveSize 1
            val metadata = response.body?.get(0)!!
            metadata.journalpostId shouldBe null
            metadata.dokumentreferanse shouldBe originalDokument.dokumentreferanse
            metadata.status shouldBe DokumentStatusDto.UNDER_REDIGERING
        }

        assertSoftly("Valider metatadata for original forsendelse") {
            val response = utførHentDokumentMetadata(originalForsendelse.forsendelseId.toString(), originalDokument.dokumentreferanse)
            response.statusCode shouldBe HttpStatus.OK
            val resBody = response.body!!
            resBody shouldHaveSize 1
            val metadata = response.body?.get(0)!!
            metadata.journalpostId shouldBe null
            metadata.dokumentreferanse shouldBe originalDokument.dokumentreferanse
            metadata.status shouldBe DokumentStatusDto.UNDER_REDIGERING
        }
    }

    @Test
    fun `skal hente dokument`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.KONTROLLERT,
                        dokumentreferanseOriginal = "123213213",
                        journalpostId = "515325325325",
                        arkivsystem = DokumentArkivSystem.JOARK
                    )
                )
            )
        )

        val respons = utførHentDokument(forsendelse.forsendelseId.toString(), forsendelse.dokumenter[0].dokumentreferanse)

        respons.statusCode shouldBe HttpStatus.OK
        respons.headers.contentDisposition.filename shouldBe "${forsendelse.dokumenter[0].dokumentreferanse}.pdf"
        respons.body shouldBe DOKUMENT_FIL.toByteArray()
    }

    @Test
    fun `skal feile å hente dokument hvis ikke har status KONTROLLERT`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        dokumentreferanseOriginal = "123213213",
                        journalpostId = "515325325325",
                        arkivsystem = DokumentArkivSystem.JOARK
                    )
                )
            )
        )

        val respons = utførHentDokument(forsendelse.forsendelseId.toString(), forsendelse.dokumenter[0].dokumentreferanse)

        respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        respons.headers["Warning"]!![0] shouldBe "Fant ikke dokument: Kan ikke hente dokument ${forsendelse.dokumenter[0].dokumentreferanse} med forsendelseId ${forsendelse.forsendelseId} fra arkivsystem = JOARK"
    }

    @Test
    fun `skal få tilgangsfeil ved henting av dokument hvis ikke har tilgang til forsendelse`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.KONTROLLERT,
                        dokumentreferanseOriginal = "123213213",
                        journalpostId = "515325325325",
                        arkivsystem = DokumentArkivSystem.JOARK
                    )
                )
            )
        )

        stubUtils.stubTilgangskontrollSak(false)
        val respons = utførHentDokument(forsendelse.forsendelseId.toString(), forsendelse.dokumenter[0].dokumentreferanse)

        respons.statusCode shouldBe HttpStatus.FORBIDDEN
    }

    @Test
    fun `skal hente statisk dokument`() {
        stubUtils.stubHentDokumetFraBestill(DOKUMENTMAL_STATISK_VEDLEGG)
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.FERDIGSTILT,
                        arkivsystem = DokumentArkivSystem.BIDRAG,
                        dokumentMalId = DOKUMENTMAL_STATISK_VEDLEGG,
                        metadata = run {
                            val metadata = DokumentMetadataDo()
                            metadata.markerSomStatiskDokument()
                            metadata
                        }
                    )
                )
            )
        )

        val respons = utførHentDokument(forsendelse.forsendelseId.toString(), forsendelse.dokumenter[0].dokumentreferanse)
        stubUtils.Valider().hentDokumentFraBestillingKalt(DOKUMENTMAL_STATISK_VEDLEGG)
        respons.statusCode shouldBe HttpStatus.OK
        respons.headers.contentDisposition.filename shouldBe "${forsendelse.dokumenter[0].dokumentreferanse}.pdf"
        respons.body shouldBe DOKUMENT_FIL.toByteArray()
    }

    @Test
    fun `skal hente statisk dokument redigerbar`() {
        stubUtils.stubHentDokumetFraBestill(DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR)
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        arkivsystem = DokumentArkivSystem.BIDRAG,
                        dokumentMalId = DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR,
                        metadata = run {
                            val metadata = DokumentMetadataDo()
                            metadata.markerSomStatiskDokument()
                            metadata.markerSomSkjema()
                            metadata
                        }
                    )
                )
            )
        )

        val respons = utførHentDokument(forsendelse.forsendelseId.toString(), forsendelse.dokumenter[0].dokumentreferanse)
        stubUtils.Valider().hentDokumentFraBestillingKalt(DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR)
        respons.statusCode shouldBe HttpStatus.OK
        respons.headers.contentDisposition.filename shouldBe "${forsendelse.dokumenter[0].dokumentreferanse}.pdf"
        respons.body shouldBe DOKUMENT_FIL.toByteArray()
    }

    @Test
    fun `skal hente statisk dokument redigerbar fra bucket hvis kontrollert`() {
        stubUtils.stubHentDokumetFraBestill(DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR)
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.KONTROLLERT,
                        arkivsystem = DokumentArkivSystem.BIDRAG,
                        dokumentMalId = DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR,
                        metadata = run {
                            val metadata = DokumentMetadataDo()
                            metadata.markerSomStatiskDokument()
                            metadata.markerSomSkjema()
                            metadata
                        }
                    )
                )
            )
        )

        val respons = utførHentDokument(forsendelse.forsendelseId.toString(), forsendelse.dokumenter[0].dokumentreferanse)
        verify(exactly = 1) { gcpCloudStorage.hentFil(eq("dokumenter/${forsendelse.dokumenter[0].filsti}")) }
        respons.statusCode shouldBe HttpStatus.OK
        respons.headers.contentDisposition.filename shouldBe "${forsendelse.dokumenter[0].dokumentreferanse}.pdf"
        respons.body shouldBe DOKUMENT_FIL.toByteArray()
    }
}
