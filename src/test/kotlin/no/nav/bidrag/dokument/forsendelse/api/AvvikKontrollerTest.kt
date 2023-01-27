package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.dto.AvvikType
import no.nav.bidrag.dokument.dto.Journalstatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utils.med
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.HttpStatus


class AvvikKontrollerTest : KontrollerTestRunner() {

    @Test
    fun `Skal hente avvik for forsendelse`() {

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
        }

        val respons = utførHentAvvik(forsendelse.forsendelseId.toString())
        respons.statusCode shouldBe HttpStatus.OK

        respons.body!! shouldHaveSize 1
        respons.body!![0] shouldBe AvvikType.FEILFORE_SAK
    }

    @Test
    fun `Skal utføre avvik for forsendelse`() {

        val saksnummer = "13213213213"

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
        }

        testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
        }

        val respons = utførHentAvvik(forsendelse.forsendelseId.toString())
        respons.statusCode shouldBe HttpStatus.OK

        respons.body!! shouldHaveSize 1
        respons.body!![0] shouldBe AvvikType.FEILFORE_SAK

        val forsendelseListe = utførHentJournalForSaksnummer(saksnummer)
        forsendelseListe.statusCode shouldBe HttpStatus.OK
        forsendelseListe.body!! shouldHaveSize 2

        val responsUtfør = utførAvbrytForsendelseAvvik(forsendelse.forsendelseId.toString())
        responsUtfør.statusCode shouldBe HttpStatus.OK

        val responsEtter = utførHentAvvik(forsendelse.forsendelseId.toString())
        responsEtter.statusCode shouldBe HttpStatus.OK

        responsEtter.body!! shouldHaveSize 0

        val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)

        forsendelseEtter!!.status shouldBe ForsendelseStatus.AVBRUTT


        val forsendelseListeEtter = utførHentJournalForSaksnummer(saksnummer)
        forsendelseListeEtter.statusCode shouldBe HttpStatus.OK
        forsendelseListeEtter.body!! shouldHaveSize 2

        forsendelseListeEtter.body!!.filter { it.journalstatus == Journalstatus.FEILREGISTRERT } shouldHaveSize 1


    }

    @ParameterizedTest
    @EnumSource(value = ForsendelseStatus::class, names = ["UNDER_PRODUKSJON", "AVBRUTT"], mode = EnumSource.Mode.EXCLUDE)
    fun `Skal hente tom liste med avvik for forsendelse med status {argumentsWithNames}`(status: ForsendelseStatus) {

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            med status status
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
        }

        val respons = utførHentAvvik(forsendelse.forsendelseId.toString())
        respons.statusCode shouldBe HttpStatus.OK

        respons.body!! shouldHaveSize 0
    }

}