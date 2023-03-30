package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.dokument.dto.AvvikType
import no.nav.bidrag.dokument.dto.Fagomrade
import no.nav.bidrag.dokument.dto.Journalstatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.med
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class AvvikKontrollerTest : KontrollerTestRunner() {

    @Test
    fun `Skal hente avvik for forsendelse`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
        }

        val respons = utførHentAvvik(forsendelse.forsendelseId.toString())
        respons.statusCode shouldBe HttpStatus.OK

        respons.body!! shouldHaveSize 3
        respons.body!! shouldContain AvvikType.FEILFORE_SAK
        respons.body!! shouldContain AvvikType.ENDRE_FAGOMRADE
        respons.body!! shouldContain AvvikType.SLETT_JOURNALPOST
    }

    @Test
    fun `Skal utføre avvik FEILFORE_SAK for forsendelse`() {
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

        respons.body!! shouldHaveSize 3
        respons.body!! shouldContain AvvikType.FEILFORE_SAK

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
        forsendelseEtter.avbruttAvIdent shouldBe SAKSBEHANDLER_IDENT
        forsendelseEtter.avbruttTidspunkt shouldNotBe null
        forsendelseEtter.avbruttTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()

        val forsendelseListeEtter = utførHentJournalForSaksnummer(saksnummer)
        forsendelseListeEtter.statusCode shouldBe HttpStatus.OK
        forsendelseListeEtter.body!! shouldHaveSize 2

        forsendelseListeEtter.body!!.filter { it.journalstatus == Journalstatus.FEILREGISTRERT } shouldHaveSize 1
    }

    @Test
    fun `Skal utføre avvik SLETT_JOURNALPOST for forsendelse`() {
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

        respons.body!! shouldHaveSize 3
        respons.body!! shouldContain AvvikType.SLETT_JOURNALPOST

        val forsendelseListe = utførHentJournalForSaksnummer(saksnummer)
        forsendelseListe.statusCode shouldBe HttpStatus.OK
        forsendelseListe.body!! shouldHaveSize 2

        val responsUtfør = utførSlettJournalpostForsendelseAvvik(forsendelse.forsendelseId.toString())
        responsUtfør.statusCode shouldBe HttpStatus.OK

        val responsEtter = utførHentAvvik(forsendelse.forsendelseId.toString())
        responsEtter.statusCode shouldBe HttpStatus.OK

        responsEtter.body!! shouldHaveSize 0

        val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)

        forsendelseEtter!!.status shouldBe ForsendelseStatus.SLETTET
        forsendelseEtter.avbruttAvIdent shouldBe SAKSBEHANDLER_IDENT
        forsendelseEtter.avbruttTidspunkt shouldNotBe null
        forsendelseEtter.avbruttTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()

        val forsendelseListeEtter = utførHentJournalForSaksnummer(saksnummer)
        forsendelseListeEtter.statusCode shouldBe HttpStatus.OK
        forsendelseListeEtter.body!! shouldHaveSize 1
    }

    @Test
    fun `Skal utføre avvik ENDRE_FAGOMRÅDE for forsendelse til tema FAR`() {
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

        respons.body!! shouldHaveSize 3
        respons.body!! shouldContain AvvikType.ENDRE_FAGOMRADE

        val forsendelseListe = utførHentJournalForSaksnummer(saksnummer)
        forsendelseListe.statusCode shouldBe HttpStatus.OK
        forsendelseListe.body!! shouldHaveSize 2

        val responsUtfør = utførEndreFagområdeForsendelseAvvik(forsendelse.forsendelseId.toString(), Fagomrade.FARSKAP)
        responsUtfør.statusCode shouldBe HttpStatus.OK

        val responsEtter = utførHentAvvik(forsendelse.forsendelseId.toString())
        responsEtter.statusCode shouldBe HttpStatus.OK

        responsEtter.body!! shouldHaveSize 3

        val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)

        forsendelseEtter!!.tema shouldBe ForsendelseTema.FAR

        val forsendelseListeEtter = utførHentJournalForSaksnummer(saksnummer, listOf("FAR", "BID"))
        forsendelseListeEtter.statusCode shouldBe HttpStatus.OK
        forsendelseListeEtter.body!! shouldHaveSize 2
        forsendelseListeEtter.body!![0].fagomrade shouldBe "FAR"
    }

    @Test
    fun `Skal utføre avvik ENDRE_FAGOMRÅDE fra FAR til BID`() {
        val saksnummer = "13213213213"

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            med tema ForsendelseTema.FAR
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
        }
        forsendelse.tema shouldBe ForsendelseTema.FAR

        val responsUtfør = utførEndreFagområdeForsendelseAvvik(forsendelse.forsendelseId.toString(), Fagomrade.BIDRAG)
        responsUtfør.statusCode shouldBe HttpStatus.OK

        val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)

        forsendelseEtter!!.tema shouldBe ForsendelseTema.BID

        val forsendelseListeEtter = utførHentJournalForSaksnummer(saksnummer, listOf("FAR", "BID"))
        forsendelseListeEtter.statusCode shouldBe HttpStatus.OK
        forsendelseListeEtter.body!! shouldHaveSize 1
        forsendelseListeEtter.body!![0].fagomrade shouldBe "BID"
    }

    @Test
    fun `Skal utføre avvik ENDRE_FAGOMRÅDE til samme tema`() {
        val saksnummer = "13213213213"

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            med tema ForsendelseTema.FAR
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
        }
        forsendelse.tema shouldBe ForsendelseTema.FAR

        val responsUtfør = utførEndreFagområdeForsendelseAvvik(forsendelse.forsendelseId.toString(), Fagomrade.FARSKAP)
        responsUtfør.statusCode shouldBe HttpStatus.OK

        val forsendelseEtter = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)

        forsendelseEtter!!.tema shouldBe ForsendelseTema.FAR

        val forsendelseListeEtter = utførHentJournalForSaksnummer(saksnummer, listOf("FAR", "BID"))
        forsendelseListeEtter.statusCode shouldBe HttpStatus.OK
        forsendelseListeEtter.body!! shouldHaveSize 1
        forsendelseListeEtter.body!![0].fagomrade shouldBe "FAR"
    }

    @Test
    fun `Skal ikke utføre avvik ENDRE_FAGOMRÅDE for forsendelse hvis tema er ikke bidrag tema`() {
        val saksnummer = "13213213213"

        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            med saksnummer saksnummer
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
        }

        val responsUtfør = utførEndreFagområdeForsendelseAvvik(forsendelse.forsendelseId.toString(), "BAR")
        responsUtfør.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @ParameterizedTest
    @EnumSource(value = ForsendelseStatus::class, names = ["UNDER_PRODUKSJON"], mode = EnumSource.Mode.EXCLUDE)
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
