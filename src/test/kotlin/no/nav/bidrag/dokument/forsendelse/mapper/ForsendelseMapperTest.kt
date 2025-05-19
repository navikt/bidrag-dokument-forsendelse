package no.nav.bidrag.dokument.forsendelse.mapper

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import no.nav.bidrag.transport.dokument.DokumentStatusDto
import no.nav.bidrag.transport.dokument.forsendelse.DokumentStatusTo
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ForsendelseMapperTest {
    @Test
    fun `Skal mappe forsendelse med flere dokumenter`() {
        val dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
        val origForsendelseId = "12321312333"
        val originalDokumentReferanse = "53454545454"
        val dokumentId1 = 1L
        val dokumentId2 = 2L
        val dokumentId3 = 3L
        val forsendelse =
            opprettForsendelse2(
                medId = true,
                dokumenter =
                    listOf(
                        nyttDokument(
                            journalpostId = null,
                            dokumentreferanseOriginal = null,
                            dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                            rekkefølgeIndeks = 1,
                            dokumentDato = dokumentDato,
                        ).copy(dokumentId = dokumentId1),
                        nyttDokument(
                            dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                            dokumentreferanseOriginal = "123213123",
                            journalpostId = "123123213123",
                            arkivsystem = DokumentArkivSystem.JOARK,
                            rekkefølgeIndeks = 0,
                            dokumentDato = dokumentDato,
                        ).copy(dokumentId = dokumentId2),
                        nyttDokument(
                            journalpostId = origForsendelseId,
                            dokumentreferanseOriginal = originalDokumentReferanse,
                            dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                            arkivsystem = DokumentArkivSystem.FORSENDELSE,
                            dokumentDato = dokumentDato,
                            dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                            rekkefølgeIndeks = 2,
                        ).copy(dokumentId = dokumentId3),
                    ),
            )

        val dokumentMetadata =
            mapOf(
                "BIF$dokumentId2" to
                    run {
                        val metadata = DokumentDtoMetadata()
                        metadata.oppdaterOriginalJournalpostId("orig_jp_123")
                        metadata.oppdaterOriginalDokumentreferanse("orig_dokref_123")
                        metadata
                    },
            )
        val forsendelseRespons = forsendelse.tilForsendelseRespons(dokumentMetadata)
        assertSoftly("Skal validere dokument 2 i forsendelse") {
            val dokumenter = forsendelseRespons.dokumenter
            val dokument1 = dokumenter[0]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[1].dokumentreferanse
            dokument1.status shouldBe DokumentStatusTo.MÅ_KONTROLLERES
            dokument1.tittel shouldBe forsendelse.dokumenter[1].tittel
            dokument1.arkivsystem shouldBe DokumentArkivSystemDto.JOARK
            dokument1.forsendelseId shouldBe "1"
            dokument1.originalJournalpostId shouldBe "orig_jp_123"
            dokument1.originalDokumentreferanse shouldBe "orig_dokref_123"
        }
        assertSoftly("Skal validere dokument 3 i forsendelse") {
            val dokumenter = forsendelseRespons.dokumenter
            val dokument1 = dokumenter[2]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[2].dokumentreferanse
            dokument1.status shouldBe DokumentStatusTo.UNDER_REDIGERING
            dokument1.tittel shouldBe forsendelse.dokumenter[2].tittel
            dokument1.dokumentDato shouldBe dokumentDato
            dokument1.arkivsystem shouldBe DokumentArkivSystemDto.FORSENDELSE
            dokument1.forsendelseId shouldBe origForsendelseId
            dokument1.originalJournalpostId shouldBe origForsendelseId
            dokument1.originalDokumentreferanse shouldBe originalDokumentReferanse
        }

        val journalpostResponse = forsendelse.tilJournalpostDto(dokumentMetadata)
        assertSoftly("Skal validere dokument 2 i forsendelse på journalpost respons") {
            val dokumenter = journalpostResponse.dokumenter
            val dokument1 = dokumenter[0]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[1].dokumentreferanse
            dokument1.journalpostId shouldBe forsendelse.forsendelseIdMedPrefix
            dokument1.status shouldBe DokumentStatusDto.UNDER_REDIGERING
            dokument1.tittel shouldBe forsendelse.dokumenter[1].tittel
            dokument1.arkivSystem shouldBe DokumentArkivSystemDto.JOARK
            dokument1.metadata["originalJournalpostId"] shouldBe "orig_jp_123"
            dokument1.metadata["originalDokumentreferanse"] shouldBe "orig_dokref_123"
        }
        assertSoftly("Skal validere dokument 3 i forsendelse på journalpostrespons") {
            val dokumenter = journalpostResponse.dokumenter
            val dokument1 = dokumenter[2]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[2].dokumentreferanse
            dokument1.journalpostId shouldBe forsendelse.forsendelseIdMedPrefix
            dokument1.status shouldBe DokumentStatusDto.UNDER_REDIGERING
            dokument1.tittel shouldBe forsendelse.dokumenter[2].tittel
            dokument1.dokumentmalId shouldBe DOKUMENTMAL_UTGÅENDE
            dokument1.arkivSystem shouldBe DokumentArkivSystemDto.FORSENDELSE
            dokument1.metadata shouldBe emptyMap()
        }
    }

    @Test
    @Disabled("Tittel på forsendelse skal alltid være lik hoveddokument")
    fun `Skal mappe forsendelse med forsendelse tittel`() {
        val forsendelse =
            opprettForsendelse2(
                tittel = "Forsendelse tittel",
            )

        val forsendelseRespons = forsendelse.tilForsendelseRespons()
        forsendelseRespons.tittel shouldBe "Forsendelse tittel"

        val journalpostResponse = forsendelse.tilJournalpostDto()
        journalpostResponse.innhold shouldBe "Forsendelse tittel"
    }

    @Test
    fun `Skal mappe forsendelse med hoveddokument tittel hvis forsendelse ikke har tittel`() {
        val forsendelse =
            opprettForsendelse2(
                medId = true,
                tittel = null,
                dokumenter =
                    listOf(
                        nyttDokument(
                            tittel = "Hoveddokument tittel",
                            rekkefølgeIndeks = 0,
                        ),
                        nyttDokument(
                            tittel = "Vedlegg 1 tittel",
                            rekkefølgeIndeks = 1,
                        ),
                    ),
            )

        val forsendelseRespons = forsendelse.tilForsendelseRespons()
        forsendelseRespons.tittel shouldBe "Hoveddokument tittel"

        val journalpostResponse = forsendelse.tilJournalpostDto()
        journalpostResponse.innhold shouldBe "Hoveddokument tittel"
    }

    @Test
    @Disabled("Tittel skal være null")
    fun `Skal mappe forsendelse med standard tittel hvis det er ingen dokumenter`() {
        val forsendelse =
            opprettForsendelse2(
                tittel = null,
                dokumenter = emptyList(),
            ).copy(forsendelseId = 100L)

        val forsendelseRespons = forsendelse.tilForsendelseRespons()
        forsendelseRespons.tittel shouldBe "Forsendelse 100"

        val journalpostResponse = forsendelse.tilJournalpostDto()
        journalpostResponse.innhold shouldBe "Forsendelse 100"
    }

    @Test
    fun `Skal mappe forsendelse til null hvis det er ingen dokumenter`() {
        val forsendelse =
            opprettForsendelse2(
                tittel = null,
                dokumenter = emptyList(),
            ).copy(forsendelseId = 100L)

        val forsendelseRespons = forsendelse.tilForsendelseRespons()
        forsendelseRespons.tittel shouldBe null

        val journalpostResponse = forsendelse.tilJournalpostDto()
        journalpostResponse.innhold shouldBe null
    }

    @Test
    fun `Skal mappe forsendelse med dokumenter i riktig rekkefølge og uten slettet dokumenter`() {
        val forsendelse =
            opprettForsendelse2(
                tittel = null,
                dokumenter =
                    listOf(
                        nyttDokument(
                            tittel = "Hoveddokument tittel",
                            rekkefølgeIndeks = 0,
                        ).copy(dokumentId = 1L),
                        nyttDokument(
                            tittel = "Vedlegg 2 tittel",
                            rekkefølgeIndeks = 2,
                        ).copy(dokumentId = 2L),
                        nyttDokument(
                            tittel = "Vedlegg 1 tittel",
                            rekkefølgeIndeks = 1,
                        ).copy(dokumentId = 3L),
                        nyttDokument(
                            tittel = "Vedlegg 3 slettet",
                            rekkefølgeIndeks = 1,
                            slettet = true,
                        ).copy(dokumentId = 4L),
                    ),
            ).copy(forsendelseId = 100L)

        val forsendelseRespons = forsendelse.tilForsendelseRespons()

        assertSoftly("Forsendelse response") {
            val dokumenter = forsendelseRespons.dokumenter
            dokumenter shouldHaveSize 3
            dokumenter[0].dokumentreferanse shouldBe "BIF1"
            dokumenter[2].dokumentreferanse shouldBe "BIF2"
            dokumenter[1].dokumentreferanse shouldBe "BIF3"
        }

        val journalpostResponse = forsendelse.tilJournalpostDto()

        assertSoftly("Journalpost response") {
            val dokumenter = journalpostResponse.dokumenter
            dokumenter shouldHaveSize 3
            dokumenter[0].dokumentreferanse shouldBe "BIF1"
            dokumenter[2].dokumentreferanse shouldBe "BIF2"
            dokumenter[1].dokumentreferanse shouldBe "BIF3"
        }
    }
}
