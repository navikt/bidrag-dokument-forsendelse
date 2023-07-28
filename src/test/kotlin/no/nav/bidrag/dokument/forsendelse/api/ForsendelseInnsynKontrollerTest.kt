package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.dokument.dto.AktorDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDtoIdType
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.mapper.DokumentDtoMetadata
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE1
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE2
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_ADRESSELINJE3
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_LANDKODE
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_POSTNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.ADRESSE_POSTSTED
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.GJELDER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.JOURNALFØRENDE_ENHET
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.MOTTAKER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_NAVN
import no.nav.bidrag.dokument.forsendelse.utils.SAKSNUMMER
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_1
import no.nav.bidrag.dokument.forsendelse.utils.med
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettAdresseDo
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.domain.string.TEMA_BIDRAG
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime

class ForsendelseInnsynKontrollerTest : KontrollerTestRunner() {

    @Test
    fun `Skal hente forsendelse`() {
        val dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                mottaker = Mottaker(
                    ident = MOTTAKER_IDENT,
                    navn = MOTTAKER_NAVN,
                    adresse = opprettAdresseDo()
                ),
                tittel = "Forsendelse tittel",
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "Tittel dokument under redigering",
                        dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                        dokumentDato = dokumentDato,
                        arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                    ),
                    nyttDokument(
                        journalpostId = "12312355555",
                        dokumentreferanseOriginal = "123123213",
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                        tittel = "Dokument må kontrolleres",
                        dokumentDato = dokumentDato,
                        arkivsystem = DokumentArkivSystem.JOARK
                    ),
                )
            )
        )
        val response = utførHentForsendelse(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!

        val dokumenter = forsendelseResponse.dokumenter
        assertSoftly {
            forsendelseResponse.forsendelseType shouldBe ForsendelseTypeTo.UTGÅENDE
            forsendelseResponse.status shouldBe ForsendelseStatusTo.UNDER_PRODUKSJON
            forsendelseResponse.behandlingInfo shouldBe null
            forsendelseResponse.dokumentDato!! shouldBeEqual dokumentDato.toLocalDate()
            forsendelseResponse.tema shouldBe TEMA_BIDRAG.verdi
            forsendelseResponse.enhet shouldBe JOURNALFØRENDE_ENHET
            forsendelseResponse.opprettetAvIdent shouldBe SAKSBEHANDLER_IDENT
            forsendelseResponse.opprettetAvNavn shouldBe SAKSBEHANDLER_NAVN
            forsendelseResponse.tittel shouldBe "Forsendelse tittel"
            forsendelseResponse.gjelderIdent shouldBe GJELDER_IDENT
            forsendelseResponse.mottaker?.ident shouldBe MOTTAKER_IDENT
            forsendelseResponse.mottaker?.navn shouldBe MOTTAKER_NAVN
            val adresse = forsendelseResponse.mottaker?.adresse!!
            adresse.adresselinje1 shouldBe ADRESSE_ADRESSELINJE1
            adresse.adresselinje2 shouldBe ADRESSE_ADRESSELINJE2
            adresse.adresselinje3 shouldBe ADRESSE_ADRESSELINJE3
            adresse.postnummer shouldBe ADRESSE_POSTNUMMER
            adresse.poststed shouldBe ADRESSE_POSTSTED
            adresse.landkode shouldBe ADRESSE_LANDKODE
            adresse.landkode3 shouldBe null
            dokumenter shouldHaveSize 2
        }

        assertSoftly("Skal validere dokument 1 i forsendelse") {
            val dokument1 = dokumenter[0]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[0].dokumentreferanse
            dokument1.tittel shouldBe forsendelse.dokumenter[0].tittel
            dokument1.status shouldBe DokumentStatusTo.UNDER_REDIGERING
            dokument1.dokumentDato shouldBe dokumentDato
            dokument1.arkivsystem shouldBe DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
            dokument1.forsendelseId shouldBe forsendelse.forsendelseId.toString()
            dokument1.originalDokumentreferanse shouldBe null
            dokument1.originalJournalpostId shouldBe null
        }

        assertSoftly("Skal validere dokument 2 i forsendelse") {
            val dokument1 = dokumenter[1]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[1].dokumentreferanse
            dokument1.status shouldBe DokumentStatusTo.MÅ_KONTROLLERES
            dokument1.tittel shouldBe forsendelse.dokumenter[1].tittel
            dokument1.dokumentDato shouldBe dokumentDato
            dokument1.arkivsystem shouldBe DokumentArkivSystemDto.JOARK
            dokument1.forsendelseId shouldBe forsendelse.forsendelseId.toString()
            dokument1.originalDokumentreferanse shouldBe "123123213"
            dokument1.originalJournalpostId shouldBe "12312355555"
        }
    }

    @Test
    fun `Skal hente forsendelse med behandlingInfo`() {
        val soknadId = "12321312"
        val vedtakId = "565656"
        val behandlingId = "343434"
        val dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                mottaker = Mottaker(
                    ident = MOTTAKER_IDENT,
                    navn = MOTTAKER_NAVN,
                    adresse = opprettAdresseDo()
                ),
                tittel = "Forsendelse tittel",
                behandlingInfo = BehandlingInfo(
                    vedtakId = vedtakId,
                    soknadFra = SoknadFra.BIDRAGSMOTTAKER,
                    soknadId = soknadId,
                    behandlingId = behandlingId,
                    stonadType = StonadType.BIDRAG,
                ),
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "Tittel dokument under redigering",
                        dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                        dokumentDato = dokumentDato,
                        arkivsystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                    ),
                )
            )
        )
        val response = utførHentForsendelse(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!

        assertSoftly {
            val behandlingInfo = forsendelseResponse.behandlingInfo
            behandlingInfo shouldNotBe null
            behandlingInfo!!.behandlingId shouldBe behandlingId
            behandlingInfo.soknadId shouldBe soknadId
            behandlingInfo.vedtakId shouldBe vedtakId
            behandlingInfo.behandlingType shouldBe StonadType.BIDRAG.name
        }

    }

    @Test
    fun `Skal hente forsendelse med lenket dokumenter`() {
        val dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")

        val originalForsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = dokumentDato
                    ),
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
        val response = utførHentForsendelse(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!

        val dokumenter = forsendelseResponse.dokumenter
        dokumenter shouldHaveSize 3
        assertSoftly("Skal validere dokument 1 i forsendelse") {
            val dokument1 = dokumenter[0]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[0].dokumentreferanse
            dokument1.tittel shouldBe forsendelse.dokumenter[0].tittel
            dokument1.status shouldBe DokumentStatusTo.UNDER_REDIGERING
            dokument1.dokumentDato shouldBe dokumentDato
            dokument1.arkivsystem shouldBe DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
            dokument1.forsendelseId shouldBe forsendelse.forsendelseId.toString()
            dokument1.originalDokumentreferanse shouldBe null
            dokument1.originalJournalpostId shouldBe null
        }

        assertSoftly("Skal validere dokument 2 i forsendelse") {
            val dokument1 = dokumenter[1]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[1].dokumentreferanse
            dokument1.status shouldBe DokumentStatusTo.MÅ_KONTROLLERES
            dokument1.tittel shouldBe forsendelse.dokumenter[1].tittel
            dokument1.dokumentDato shouldBe dokumentDato
            dokument1.arkivsystem shouldBe DokumentArkivSystemDto.JOARK
            dokument1.forsendelseId shouldBe forsendelse.forsendelseId.toString()
            dokument1.originalDokumentreferanse shouldBe "123213123"
            dokument1.originalJournalpostId shouldBe "123123213123"
        }

        assertSoftly("Skal validere dokument 3 i forsendelse") {
            val dokument1 = dokumenter[2]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[2].dokumentreferanse
            dokument1.status shouldBe DokumentStatusTo.UNDER_REDIGERING
            dokument1.tittel shouldBe forsendelse.dokumenter[2].tittel
            dokument1.dokumentDato shouldBe dokumentDato
            dokument1.arkivsystem shouldBe DokumentArkivSystemDto.FORSENDELSE
            dokument1.forsendelseId shouldBe originalForsendelse.forsendelseId.toString()
            dokument1.originalJournalpostId shouldBe originalForsendelse.forsendelseId.toString()
            dokument1.originalDokumentreferanse shouldBe originalDokument.dokumentreferanse
        }
    }

    @Test
    fun `Skal hente forsendelse som journalpost`() {
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    )
                )
            )
        )
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!.journalpost!!

        assertSoftly {
            forsendelseResponse.dokumentDato!! shouldHaveSameDayAs LocalDate.now()
            forsendelseResponse.innhold shouldBe TITTEL_HOVEDDOKUMENT
            forsendelseResponse.gjelderIdent shouldBe GJELDER_IDENT
            forsendelseResponse.gjelderAktor shouldBe AktorDto(GJELDER_IDENT)
            forsendelseResponse.avsenderMottaker shouldBe AvsenderMottakerDto(
                MOTTAKER_NAVN,
                MOTTAKER_IDENT,
                AvsenderMottakerDtoIdType.FNR
            )
            forsendelseResponse.brevkode?.kode shouldBe HOVEDDOKUMENT_DOKUMENTMAL
            forsendelseResponse.journalpostId shouldBe "BIF-${forsendelse.forsendelseId}"
            forsendelseResponse.journalstatus shouldBe "D"
            forsendelseResponse.dokumentType shouldBe "U"
            forsendelseResponse.journalforendeEnhet shouldBe JOURNALFØRENDE_ENHET
            forsendelseResponse.sakstilknytninger shouldContain SAKSNUMMER
            response.body!!.sakstilknytninger shouldContain SAKSNUMMER
            forsendelseResponse.fagomrade shouldBe "BID"
            forsendelseResponse.journalfortDato?.shouldHaveSameDayAs(LocalDate.now())
            forsendelseResponse.språk shouldBe "NB"
            forsendelseResponse.journalfortAv shouldBe SAKSBEHANDLER_IDENT
            forsendelseResponse.opprettetAvIdent shouldBe SAKSBEHANDLER_IDENT
            forsendelseResponse.dokumenter shouldHaveSize 1

            val hoveddokument = forsendelseResponse.dokumenter[0]
            hoveddokument.tittel shouldBe TITTEL_HOVEDDOKUMENT
            hoveddokument.arkivSystem shouldBe DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
            hoveddokument.journalpostId shouldBe forsendelseResponse.journalpostId
            hoveddokument.dokumentmalId shouldBe HOVEDDOKUMENT_DOKUMENTMAL
            hoveddokument.dokumentreferanse shouldBe forsendelse.dokumenter[0].dokumentreferanse
        }
    }

    @Test
    fun `Skal hente forsendelse med dokumenter knyttet til annen forsendelse og ekstern dokumenter`() {
        val originalForsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    ),
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
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    ),
                    nyttDokument(
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentreferanseOriginal = "123213123",
                        journalpostId = "123123213123",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    ),
                    nyttDokument(
                        journalpostId = originalForsendelse.forsendelseId.toString(),
                        dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    )
                )
            )
        )
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!.journalpost!!

        val dokumenter = forsendelseResponse.dokumenter
        assertSoftly {
            val dokument1 = dokumenter[0]
            dokument1.dokumentreferanse shouldBe forsendelse.dokumenter[0].dokumentreferanse
            dokument1.metadata shouldBe emptyMap()

            val dokument2 = dokumenter[1]
            dokument2.dokumentreferanse shouldBe forsendelse.dokumenter[1].dokumentreferanse
            dokument2.metadata shouldNotBe null
            dokument2.arkivSystem shouldBe DokumentArkivSystemDto.JOARK
            val metadata2 = DokumentDtoMetadata.from(dokument2.metadata)
            metadata2.hentOriginalDokumentreferanse() shouldBe "123213123"
            metadata2.hentOriginalJournalpostId() shouldBe "123123213123"

            val dokument3 = dokumenter[2]
            dokument3.dokumentreferanse shouldBe forsendelse.dokumenter[2].dokumentreferanse
            dokument3.metadata shouldNotBe null
            dokument3.arkivSystem shouldBe DokumentArkivSystemDto.FORSENDELSE
            val metadata3 = DokumentDtoMetadata.from(dokument3.metadata)
            metadata3.hentOriginalDokumentreferanse() shouldBe originalDokument.dokumentreferanse
            metadata3.hentOriginalJournalpostId() shouldBe originalForsendelse.forsendelseId.toString()
        }
    }

    @Test
    fun `Skal hente forsendelse notat`() {
        val dokumentdato = LocalDateTime.parse("2021-01-01T01:02:03")
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                erNotat = true,
                dokumenter = listOf(
                    nyttDokument(
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = dokumentdato
                    )
                )
            )
        )
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!.journalpost!!

        assertSoftly {
            forsendelseResponse.dokumentDato!! shouldBe dokumentdato.toLocalDate()
            forsendelseResponse.journalstatus shouldBe "D"
            forsendelseResponse.dokumentType shouldBe "X"
            forsendelseResponse.innhold shouldBe TITTEL_HOVEDDOKUMENT
            forsendelseResponse.gjelderIdent shouldBe GJELDER_IDENT
            forsendelseResponse.gjelderAktor shouldBe AktorDto(GJELDER_IDENT)

            forsendelseResponse.fagomrade shouldBe "BID"
            forsendelseResponse.journalfortDato?.shouldHaveSameDayAs(LocalDate.now())
            forsendelseResponse.dokumenter shouldHaveSize 1

            val hoveddokument = forsendelseResponse.dokumenter[0]
            hoveddokument.tittel shouldBe TITTEL_HOVEDDOKUMENT
            hoveddokument.arkivSystem shouldBe DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
        }
    }

    @Test
    fun `Skal hente forsendelse med saksnummer`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString(), SAKSNUMMER)

        response.statusCode shouldBe HttpStatus.OK
    }

    @Test
    fun `Skal ikke hente forsendelse hvis forsendelse ikke har saksnummer i forespørsel`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString(), "13213123")

        response.statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `Skal returnere forsendelse med status F hvis forsendels er avbrutt`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            med status ForsendelseStatus.AVBRUTT
            +nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        response.body!!.journalpost!!.journalstatus shouldBe "F"
        response.body!!.journalpost!!.feilfort shouldBe true
    }

    @Test
    fun `Skal hente forsendelse med dokumenter i riktig rekkefølge`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(
                journalpostId = null,
                dokumentreferanseOriginal = null,
                rekkefølgeIndeks = 0,
                tittel = "HOVEDDOK"
            )
            +nyttDokument(
                journalpostId = null,
                dokumentreferanseOriginal = null,
                rekkefølgeIndeks = 1,
                tittel = "VEDLEGG1"
            )
            +nyttDokument(
                rekkefølgeIndeks = 2,
                tittel = "VEDLEGG2",
                dokumentreferanseOriginal = "4543434"
            )
            +nyttDokument(
                rekkefølgeIndeks = 4,
                slettet = true,
                tittel = "VEDLEGG4",
                dokumentreferanseOriginal = "3231312313"
            )
            +nyttDokument(
                journalpostId = "BID-123123213",
                dokumentreferanseOriginal = "12312321333",
                rekkefølgeIndeks = 3,
                tittel = "VEDLEGG3"
            )
        }
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!.journalpost!!

        assertSoftly {
            val dokumenter = forsendelseResponse.dokumenter
            dokumenter shouldHaveSize 4
            dokumenter[0].tittel shouldBe "HOVEDDOK"
            dokumenter[1].tittel shouldBe "VEDLEGG1"
            dokumenter[2].tittel shouldBe "VEDLEGG2"
            dokumenter[3].tittel shouldBe "VEDLEGG3"
        }
    }

    @Test
    fun `Utgående forsendelse skal ha status KP hvis alle dokumenter er ferdigstilt`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT)
            +nyttDokument(
                journalpostId = null,
                dokumentreferanseOriginal = null,
                dokumentStatus = DokumentStatus.FERDIGSTILT,
                tittel = TITTEL_VEDLEGG_1
            )
        }
        val response = utførHentJournalpost(forsendelse.forsendelseId.toString())

        response.statusCode shouldBe HttpStatus.OK

        val forsendelseResponse = response.body!!.journalpost!!

        assertSoftly {
            forsendelseResponse.journalstatus shouldBe "KP"

            forsendelseResponse.dokumenter shouldHaveSize 2
            val hoveddokumentForsendelse2 = forsendelseResponse.dokumenter[0]
            val vedleggForsendelse2 = forsendelseResponse.dokumenter[1]

            hoveddokumentForsendelse2.tittel shouldBe TITTEL_HOVEDDOKUMENT
            vedleggForsendelse2.tittel shouldBe TITTEL_VEDLEGG_1
            vedleggForsendelse2.dokumentreferanse shouldBe forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge[1].dokumentreferanse
        }
    }

    @Test
    fun `Skal hente forsendelser basert på saksnummer`() {
        val forsendelse1 = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "FORSENDELSE 1"
            )
        }

        val forsendelse2 = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "FORSENDELSE 2")
        }

        testDataManager.opprettOgLagreForsendelse {
            med saksnummer "5435435"
            +nyttDokument(dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }

        val response = httpHeaderTestRestTemplate.getForEntity<List<JournalpostDto>>(
            "${rootUri()}/sak/${forsendelse1.saksnummer}/journal?fagomrade=BID"
        )

        response.statusCode shouldBe HttpStatus.OK

        val journalResponse = response.body!!

        assertSoftly {
            journalResponse shouldHaveSize 2

            val forsendelseResponse1 =
                journalResponse.find { it.journalpostId == forsendelse1.forsendelseIdMedPrefix }!!
            val forsendelseResponse2 =
                journalResponse.find { it.journalpostId == forsendelse2.forsendelseIdMedPrefix }!!

            forsendelseResponse1.innhold shouldBe "FORSENDELSE 1"
            forsendelseResponse2.innhold shouldBe "FORSENDELSE 2"
        }
    }

    @Test
    fun `Skal ikke hente forsendelser som er arkivert i fagarkivet (JOARK) eller har status AVBRUTT`() {
        stubUtils.stubTilgangskontrollPerson()
        val saksnummer = "3123213123213"
        val forsendelse1 = testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            +nyttDokument(
                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                tittel = "FORSENDELSE 1"
            )
        }

        val forsendelse2 = testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "FORSENDELSE 2")
        }

        testDataManager.opprettOgLagreForsendelse {
            med arkivJournalpostId "123123213"
            med saksnummer saksnummer
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "FORSENDELSE 3")
        }

        val forsendelse3Avbrutt = testDataManager.opprettOgLagreForsendelse {
            med status ForsendelseStatus.AVBRUTT
            med saksnummer saksnummer
            +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "FORSENDELSE 4")
        }

        val response = utførHentJournalForSaksnummer(forsendelse1.saksnummer)

        response.statusCode shouldBe HttpStatus.OK

        val journalResponse = response.body!!

        assertSoftly {
            journalResponse shouldHaveSize 3

            val forsendelseResponse1 =
                journalResponse.find { it.journalpostId == "BIF-${forsendelse1.forsendelseId}" }!!
            val forsendelseResponse2 =
                journalResponse.find { it.journalpostId == "BIF-${forsendelse2.forsendelseId}" }!!
            val forsendelseResponse3 =
                journalResponse.find { it.journalpostId == "BIF-${forsendelse3Avbrutt.forsendelseId}" }!!

            forsendelseResponse1.innhold shouldBe "FORSENDELSE 1"
            forsendelseResponse2.innhold shouldBe "FORSENDELSE 2"
            forsendelseResponse3.innhold shouldBe "FORSENDELSE 4"
        }
    }

    @Test
    fun `Skal hente forsendelser basert på saksnummer med forsendelser som har lenket dokumenter`() {
        val saksnummer = SAKSNUMMER
        val originalForsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                saksnummer = saksnummer,
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    ),
                )
            )
        )
        val originalDokument = originalForsendelse.dokumenter[0]
        val forsendelse2 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                saksnummer = saksnummer,
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    ),
                    nyttDokument(
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentreferanseOriginal = "123213123",
                        journalpostId = "123123213123",
                        arkivsystem = DokumentArkivSystem.JOARK,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    ),
                    nyttDokument(
                        journalpostId = originalForsendelse.forsendelseId.toString(),
                        dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    )
                )
            )
        )

        val originalDokumentForsendelse2 = forsendelse2.dokumenter[0]

        val forsendelse3 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                saksnummer = saksnummer,
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    ),
                    nyttDokument(
                        journalpostId = originalDokumentForsendelse2.forsendelseId.toString(),
                        dokumentreferanseOriginal = originalDokumentForsendelse2.dokumentreferanse,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    ),
                    nyttDokument(
                        journalpostId = originalForsendelse.forsendelseId.toString(),
                        dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        arkivsystem = DokumentArkivSystem.FORSENDELSE,
                        dokumentDato = LocalDateTime.parse("2021-01-01T01:02:03")
                    )
                )
            )
        )
        val response = utførHentJournalForSaksnummer(saksnummer)

        response.statusCode shouldBe HttpStatus.OK

        val journalResponse = response.body!!
        journalResponse shouldHaveSize 3

        assertSoftly("Valider forsendelse 2") {
            val forsendelseResponse = journalResponse[1]
            val dokumenter = forsendelseResponse.dokumenter
            val dokument = dokumenter[0]
            dokument.dokumentreferanse shouldBe forsendelse2.dokumenter[0].dokumentreferanse
            dokument.metadata shouldBe emptyMap()

            val dokument2 = dokumenter[1]
            dokument2.dokumentreferanse shouldBe forsendelse2.dokumenter[1].dokumentreferanse
            dokument2.metadata shouldNotBe null
            dokument2.arkivSystem shouldBe DokumentArkivSystemDto.JOARK
            val metadata2 = DokumentDtoMetadata.from(dokument2.metadata)
            metadata2.hentOriginalDokumentreferanse() shouldBe "123213123"
            metadata2.hentOriginalJournalpostId() shouldBe "123123213123"

            val dokument3 = dokumenter[2]
            dokument3.dokumentreferanse shouldBe forsendelse2.dokumenter[2].dokumentreferanse
            dokument3.metadata shouldNotBe null
            dokument3.arkivSystem shouldBe DokumentArkivSystemDto.FORSENDELSE
            val metadata3 = DokumentDtoMetadata.from(dokument3.metadata)
            metadata3.hentOriginalDokumentreferanse() shouldBe originalDokument.dokumentreferanse
            metadata3.hentOriginalJournalpostId() shouldBe originalForsendelse.forsendelseId.toString()
        }

        assertSoftly("Valider forsendelse 3") {
            val forsendelseResponse = journalResponse[2]
            val dokumenter = forsendelseResponse.dokumenter
            val dokument = dokumenter[0]
            dokument.dokumentreferanse shouldBe forsendelse3.dokumenter[0].dokumentreferanse
            dokument.metadata shouldBe emptyMap()

            val dokument2 = dokumenter[1]
            dokument2.dokumentreferanse shouldBe forsendelse3.dokumenter[1].dokumentreferanse
            dokument2.metadata shouldNotBe null
            dokument2.arkivSystem shouldBe DokumentArkivSystemDto.FORSENDELSE
            val metadata2 = DokumentDtoMetadata.from(dokument2.metadata)
            metadata2.hentOriginalDokumentreferanse() shouldBe originalDokumentForsendelse2.dokumentreferanse
            metadata2.hentOriginalJournalpostId() shouldBe forsendelse2.forsendelseId.toString()

            val dokument3 = dokumenter[2]
            dokument3.dokumentreferanse shouldBe forsendelse3.dokumenter[2].dokumentreferanse
            dokument3.metadata shouldNotBe null
            dokument3.arkivSystem shouldBe DokumentArkivSystemDto.FORSENDELSE
            val metadata3 = DokumentDtoMetadata.from(dokument3.metadata)
            metadata3.hentOriginalDokumentreferanse() shouldBe originalDokument.dokumentreferanse
            metadata3.hentOriginalJournalpostId() shouldBe originalForsendelse.forsendelseId.toString()
        }
    }
}
