package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.dto.DokumentMetadata
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.FerdigstillDokumentRequest
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.DokumentMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.VALID_PDF_BASE64
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime


class RedigerForsendelseIt : KontrollerTestContainerRunner() {

    @Test
    fun `Skal ferdigstille dokument under redigering`() {
        val redigeringData = "redigeringdata"
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Tittel på dokument"
                    ).copy(metadata = DokumentMetadataDo())
                )
            )
        )

        val dokument = forsendelse.dokumenter[0]
        val response = utførFerdigstillDokument(
            forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse, request = FerdigstillDokumentRequest(
                fysiskDokument = VALID_PDF_BASE64.toByteArray(),
                redigeringMetadata = redigeringData
            )
        )

        response.statusCode shouldBe HttpStatus.OK
        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
        val dokumentResponse = utførHentDokument(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)
        val dokumentMetadataResponse = utførHentDokumentMetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)
        dokumentMetadataResponse.statusCode shouldBe HttpStatus.OK
        val dokumentMetadata = dokumentMetadataResponse.body!!
        assertSoftly {
            val oppdatertDokument = oppdatertForsendelse.dokumenter[0]
            oppdatertDokument.dokumentStatus shouldBe DokumentStatus.KONTROLLERT
            oppdatertDokument.ferdigstiltTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertDokument.ferdigstiltAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertDokument.metadata.hentRedigeringmetadata() shouldBe redigeringData
            oppdatertDokument.metadata.hentGcpKrypteringnøkkelVersjon() shouldBe "-1"
            oppdatertDokument.metadata.hentGcpFilsti() shouldBe oppdatertDokument.filsti
            String(dokumentResponse.body!!) shouldBe VALID_PDF_BASE64
            dokumentMetadata shouldHaveSize 1
            dokumentMetadata[0].journalpostId shouldBe forsendelse.forsendelseIdMedPrefix
            dokumentMetadata[0].dokumentreferanse shouldBe oppdatertDokument.dokumentreferanse
        }
    }

    @Test
    fun `Skal oppheve ferdigstilt dokument under redigering`() {
        val redigeringData = "redigeringdata"
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Tittel på dokument"
                    ).copy(metadata = DokumentMetadataDo())
                )
            )
        )

        val dokument = forsendelse.dokumenter[0]

        // Ferdigstill dokument
        val responseFerdigstill = utførFerdigstillDokument(
            forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse, request = FerdigstillDokumentRequest(
                fysiskDokument = VALID_PDF_BASE64.toByteArray(),
                redigeringMetadata = redigeringData
            )
        )

        responseFerdigstill.statusCode shouldBe HttpStatus.OK

        // Opphev ferdigstilt dokument
        val responsOpphev = utførOpphevFerdigstillDokument(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)
        responsOpphev.statusCode shouldBe HttpStatus.OK
        val oppdatertForsendelseEtterOpphev = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
        val dokumentResponseEtterOpphev = utførHentDokument(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)
        dokumentResponseEtterOpphev.statusCode shouldBe HttpStatus.BAD_REQUEST
        val dokumentMetadataEtterOpphevResponse = utførHentDokumentMetadata(forsendelse.forsendelseIdMedPrefix, dokument.dokumentreferanse)
        dokumentMetadataEtterOpphevResponse.statusCode shouldBe HttpStatus.OK
        val dokumentMetadataEtterOpphev = dokumentMetadataEtterOpphevResponse.body!!
        assertSoftly {
            val oppdaterDokumentEtterOpphev = oppdatertForsendelseEtterOpphev.dokumenter[0]
            oppdaterDokumentEtterOpphev.dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
            oppdaterDokumentEtterOpphev.metadata.hentRedigeringmetadata() shouldBe redigeringData
            oppdaterDokumentEtterOpphev.ferdigstiltTidspunkt shouldBe null
            oppdaterDokumentEtterOpphev.ferdigstiltAvIdent shouldBe null
            oppdaterDokumentEtterOpphev.metadata.hentGcpKrypteringnøkkelVersjon() shouldBe null
            oppdaterDokumentEtterOpphev.metadata.hentGcpFilsti() shouldBe null
            dokumentMetadataEtterOpphev shouldHaveSize 1
            dokumentMetadataEtterOpphev[0].journalpostId shouldBe "BID-123123"
            dokumentMetadataEtterOpphev[0].dokumentreferanse shouldBe "123213213"

        }
    }

    fun utførHentDokumentMetadata(
        forsendelseId: String,
        dokumentreferanse: String
    ): ResponseEntity<List<DokumentMetadata>> {
        return httpHeaderTestRestTemplate.optionsForEntity<List<DokumentMetadata>>(
            "${rootUri()}/dokument/$forsendelseId/$dokumentreferanse"
        )

    }

    fun utførHentDokument(
        forsendelseId: String,
        dokumentreferanse: String,
    ): ResponseEntity<ByteArray> {
        return httpHeaderTestRestTemplate.getForEntity<ByteArray>(
            "${rootUri()}/dokument/$forsendelseId/$dokumentreferanse"
        )
    }

    fun utførOpphevFerdigstillDokument(
        forsendelseId: String,
        dokumentreferanse: String,
    ): ResponseEntity<DokumentRespons> {
        return httpHeaderTestRestTemplate.patchForEntity<DokumentRespons>(
            "${rootUri()}/redigering/$forsendelseId/$dokumentreferanse/ferdigstill/opphev",
        )
    }
}