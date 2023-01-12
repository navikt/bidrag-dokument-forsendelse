package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.journalpostIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

class DistribuerKontrollerTestRunner: KontrollerTestRunner()  {
    protected fun utførHentKanDistribuere(forsendelseId: String): ResponseEntity<Void> {
        return httpHeaderTestRestTemplate.exchange("${rootUri()}/journal/distribuer/$forsendelseId/enabled", HttpMethod.GET, null, Void::class.java)
    }
    protected fun utførDistribuerForsendelse(forsendelseId: String, forespørsel: DistribuerJournalpostRequest? = null): ResponseEntity<DistribuerJournalpostResponse> {
        return httpHeaderTestRestTemplate.exchange("${rootUri()}/journal/distribuer/$forsendelseId", HttpMethod.POST, forespørsel?.let { HttpEntity(it) }, DistribuerJournalpostResponse::class.java)
    }
    @Test
    fun `skal returnere at forsendelse kan distribueres hvis forsendelse er ferdigstilt`(){
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT)
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, dokumentStatus = DokumentStatus.FERDIGSTILT)
        }

        val response = utførHentKanDistribuere(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK
    }

    @Test
    fun `skal returnere at forsendelse ikke kan distribueres hvis forsendelse er inneholder dokumenter som ikke er ferdigstilt`(){
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT)
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, dokumentStatus = DokumentStatus.UNDER_REDIGERING)
        }

        val response = utførHentKanDistribuere(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.NOT_ACCEPTABLE
    }

    @Test
    fun `skal distribuere forsendelse`(){
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            + nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
            + nyttDokument(journalpostId = null, eksternDokumentreferanse = null, dokumentStatus = DokumentStatus.FERDIGSTILT, tittel = "Tittel vedlegg", dokumentMalId = "BI100", rekkefølgeIndeks = 1, tilknyttetSom = DokumentTilknyttetSom.VEDLEGG)
        }

        stubUtils.stubOpprettJournalpost(nyJournalpostId, forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") })

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.fagrkivDokumentreferanse shouldBe "JOARK${it.dokumentreferanse}"
            }

            stubUtils.Valider().opprettJournalpostKaltMed("{" +
                    "\"skalFerdigstilles\":true," +
                    "\"gjelderIdent\":\"${forsendelse.gjelderIdent}\"," +
                    "\"avsenderMottaker\":{\"navn\":\"${forsendelse.mottaker?.navn}\",\"ident\":\"${forsendelse.mottaker?.ident}\",\"type\":\"FNR\",\"adresse\":null}," +
                    "\"dokumenter\":[" +
                    "{\"tittel\":\"Tittel på hoveddokument\",\"brevkode\":\"BI091\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}," +
                    "{\"tittel\":\"Tittel vedlegg\",\"brevkode\":\"BI100\",\"fysiskDokument\":\"SlZCRVJpMHhMamNnUW1GelpUWTBJR1Z1WTI5a1pYUWdabmx6YVhOcklHUnZhM1Z0Wlc1MA==\"}]," +
                    "\"tilknyttSaker\":[\"${forsendelse.saksnummer}\"]," +
                    "\"journalposttype\":\"UTGÅENDE\"," +
                    "\"referanseId\":\"BIF_${forsendelse.forsendelseId}\"," +
                    "\"journalførendeEnhet\":\"${forsendelse.enhet}\"" +
                    "}")
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
            stubUtils.Valider().hentDokumentKalt(forsendelse.forsendelseIdMedPrefix, forsendelse.dokumenter.vedlegger[0].dokumentreferanse)
            stubUtils.Valider().hentDokumentKalt(forsendelse.dokumenter.hoveddokument?.journalpostIdMedPrefix!!, forsendelse.dokumenter.hoveddokument?.dokumentreferanse!!)
        }
    }
}