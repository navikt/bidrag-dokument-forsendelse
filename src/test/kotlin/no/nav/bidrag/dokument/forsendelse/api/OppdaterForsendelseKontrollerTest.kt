package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_1
import no.nav.bidrag.dokument.forsendelse.utils.er
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumentDato
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime

class OppdaterForsendelseKontrollerTest : KontrollerTestRunner() {

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
    fun `Skal oppdatere og opprette dokumenter i forsendelse`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
            +nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2)
        }

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]

        val oppdaterForespørsel = OppdaterForsendelseForespørsel(
            dokumenter = listOf(
                OppdaterDokumentForespørsel(
                    dokumentreferanse = vedlegg1.dokumentreferanse,
                    fjernTilknytning = true
                ),
                OppdaterDokumentForespørsel(
                    tittel = "Ny tittel hoveddok",
                    dokumentreferanse = hoveddokument.dokumentreferanse
                ),
                OppdaterDokumentForespørsel(
                    tittel = "Ny dokument 1",
                    dokumentreferanse = "1123213213",
                    journalpostId = "JOARK-123123123"
                ),
                OppdaterDokumentForespørsel(
                    tittel = "Ny dokument 2 bestilt",
                    dokumentmalId = DOKUMENTMAL_UTGÅENDE
                )
            )
        )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 3
            val dokumenter = oppdatertForsendelse.dokumenter.sortertEtterRekkefølge
            dokumenter[0].tittel shouldBe "Ny tittel hoveddok"
            dokumenter[1].tittel shouldBe "Ny dokument 1"
            dokumenter[2].tittel shouldBe "Ny dokument 2 bestilt"

            stubUtils.Valider().bestillDokumentKaltMed(DOKUMENTMAL_UTGÅENDE)
        }
    }

    @Test
    fun `Skal oppdatere og slette dokumentene i forsendelse`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
            +nyttDokument(rekkefølgeIndeks = 1, journalpostId = null, dokumentreferanseOriginal = null)
            +nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2)
            +nyttDokument(journalpostId = "JOARK-454545", dokumentreferanseOriginal = "54545454", rekkefølgeIndeks = 3)
            +nyttDokument(journalpostId = "JOARK-25555", dokumentreferanseOriginal = "555555", rekkefølgeIndeks = 4)
        }

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]
        val vedlegg3 = forsendelse.dokumenter.vedlegger[2]
        val vedlegg4 = forsendelse.dokumenter.vedlegger[3]

        val oppdaterForespørsel = OppdaterForsendelseForespørsel(
            dokumenter = listOf(
                OppdaterDokumentForespørsel(
                    dokumentreferanse = vedlegg3.dokumentreferanse
                ),
                OppdaterDokumentForespørsel(
                    dokumentreferanse = vedlegg1.dokumentreferanse,
                    fjernTilknytning = true
                ),
                OppdaterDokumentForespørsel(
                    tittel = "Ny tittel hoveddok",
                    dokumentreferanse = hoveddokument.dokumentreferanse
                ),
                OppdaterDokumentForespørsel(
                    dokumentreferanse = vedlegg2.dokumentreferanse
                ),
                OppdaterDokumentForespørsel(
                    dokumentreferanse = vedlegg4.dokumentreferanse,
                    fjernTilknytning = true
                )
            )
        )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 4

            val dokumenter = oppdatertForsendelse.dokumenter.sortertEtterRekkefølge
            dokumenter[0].tittel shouldBe vedlegg3.tittel
            dokumenter[1].tittel shouldBe "Ny tittel hoveddok"
            dokumenter[2].tittel shouldBe vedlegg2.tittel

            dokumenter[3].slettetTidspunkt!! shouldHaveSameDayAs LocalDate.now()
        }
    }

    @Test
    fun `Skal slette dokument med ekstern referanse fra forsendelse`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
            +nyttDokument(rekkefølgeIndeks = 1)
            +nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2)
        }

        val forsendelseId = forsendelse.forsendelseId!!

        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

        val responseNyDokument = utførSlettDokumentForespørsel(forsendelseId, vedlegg1.dokumentreferanse)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe hoveddokument.tittel
            oppdatertForsendelse.dokumenter.vedlegger[0].tittel shouldBe vedlegg2.tittel

            oppdatertForsendelse.dokumenter.hoveddokument!!.rekkefølgeIndeks shouldBe 0
            oppdatertForsendelse.dokumenter.vedlegger[0].rekkefølgeIndeks shouldBe 1

            oppdatertForsendelse.dokumenter.hoveddokument!!.tilknyttetSom shouldBe DokumentTilknyttetSom.HOVEDDOKUMENT
            oppdatertForsendelse.dokumenter.vedlegger[0].tilknyttetSom shouldBe DokumentTilknyttetSom.VEDLEGG
        }

        val respons = utførHentJournalpost(forsendelseId.toString())
        respons.statusCode shouldBe HttpStatus.OK

        val forsendelseFraRespons = respons.body!!.journalpost!!

        assertSoftly {
            forsendelseFraRespons.dokumenter shouldHaveSize 2
            forsendelseFraRespons.dokumenter[0].tittel shouldBe hoveddokument.tittel
            forsendelseFraRespons.dokumenter[1].tittel shouldBe vedlegg2.tittel
        }
    }

    @Test
    fun `Skal slette hoveddokument uten ekstern referanse fra forsendelse`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
            +nyttDokument(rekkefølgeIndeks = 1)
            +nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2)
        }

        val forsendelseId = forsendelse.forsendelseId!!

        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

        val responseNyDokument = utførSlettDokumentForespørsel(forsendelseId, hoveddokument.dokumentreferanse)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 3

            val dokumenter = oppdatertForsendelse.dokumenter.sortertEtterRekkefølge
            dokumenter[0].tittel shouldBe vedlegg1.tittel
            dokumenter[1].tittel shouldBe vedlegg2.tittel
            dokumenter[2].tittel shouldBe hoveddokument.tittel
            dokumenter[2].slettetTidspunkt shouldNotBe null
            dokumenter[2].slettetTidspunkt!! shouldHaveSameDayAs LocalDate.now()

            dokumenter[0].rekkefølgeIndeks shouldBe 0
            dokumenter[1].rekkefølgeIndeks shouldBe 1
            dokumenter[2].rekkefølgeIndeks shouldBe 2
        }

        val respons = utførHentJournalpost(forsendelseId.toString())
        respons.statusCode shouldBe HttpStatus.OK

        val forsendelseFraRespons = respons.body!!.journalpost!!

        assertSoftly {
            forsendelseFraRespons.dokumenter shouldHaveSize 2
            forsendelseFraRespons.dokumenter[0].tittel shouldBe vedlegg1.tittel
            forsendelseFraRespons.dokumenter[1].tittel shouldBe vedlegg2.tittel
        }
    }

    @Test
    fun `Skal legge til dokument på forsendelse`() {
        val forsendelse = testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
        }

        val forsendelseId = forsendelse.forsendelseId!!

        val opprettDokumentForespørsel = OpprettDokumentForespørsel(
            tittel = TITTEL_VEDLEGG_1,
            dokumentmalId = "AAA"
        )

        val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe TITTEL_HOVEDDOKUMENT
            oppdatertForsendelse.dokumenter.vedlegger[0].tittel shouldBe TITTEL_VEDLEGG_1
            stubUtils.Valider().bestillDokumentKaltMed("AAA")
        }
    }

    @Test
    fun `Skal ikke oppdatere dokumentdato på utgående`() {
        val dokdatoNotat = LocalDateTime.parse("2020-01-01T01:02:03")
        val dokdatoUtgående = LocalDateTime.parse("2021-01-01T01:02:03")
        val dokdatoUtgående2 = LocalDateTime.parse("2021-01-01T01:02:03")
        val dokdatoOppdater = LocalDateTime.parse("2022-01-05T01:02:03")

        val forsendelseNotat = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                erNotat = true,
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        rekkefølgeIndeks = 0,
                        dokumentDato = dokdatoNotat
                    )
                )
            )
        )

        val forsendelseUtgående = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        rekkefølgeIndeks = 0,
                        dokumentDato = dokdatoUtgående
                    )
                )
            )
        )

        val forsendelseUtgåendeMedDokdatoIForespørsel = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        rekkefølgeIndeks = 0,
                        dokumentDato = dokdatoUtgående2
                    )
                )
            )
        )

        utførOppdaterForsendelseForespørsel(
            forsendelseNotat.forsendelseIdMedPrefix,
            OppdaterForsendelseForespørsel(
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel notat",
                        dokumentreferanse = forsendelseNotat.dokumenter[0].dokumentreferanse
                    )
                )
            )
        ).statusCode shouldBe HttpStatus.OK

        utførOppdaterForsendelseForespørsel(
            forsendelseUtgående.forsendelseIdMedPrefix,
            OppdaterForsendelseForespørsel(
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel utgående",
                        dokumentreferanse = forsendelseUtgående.dokumenter[0].dokumentreferanse
                    )
                )
            )
        ).statusCode shouldBe HttpStatus.OK

        utførOppdaterForsendelseForespørsel(
            forsendelseUtgåendeMedDokdatoIForespørsel.forsendelseIdMedPrefix,
            OppdaterForsendelseForespørsel(
                dokumentDato = dokdatoOppdater,
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel utgående",
                        dokumentreferanse = forsendelseUtgåendeMedDokdatoIForespørsel.dokumenter[0].dokumentreferanse
                    )
                )
            )
        ).statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelseNotat = testDataManager.hentForsendelse(forsendelseNotat.forsendelseId!!)!!
        val oppdatertForsendelseUtgående = testDataManager.hentForsendelse(forsendelseUtgående.forsendelseId!!)!!
        val oppdatertForsendelseUtgåendeMedDokdatoIForespørsel =
            testDataManager.hentForsendelse(forsendelseUtgåendeMedDokdatoIForespørsel.forsendelseId!!)!!

        assertSoftly {
            oppdatertForsendelseNotat.dokumenter.hoveddokument?.tittel shouldBe "Ny tittel notat"
            oppdatertForsendelseUtgående.dokumenter.hoveddokument?.tittel shouldBe "Ny tittel utgående"
            oppdatertForsendelseNotat.dokumentDato shouldBe dokdatoNotat
            oppdatertForsendelseUtgående.dokumentDato shouldBe dokdatoUtgående
            oppdatertForsendelseUtgåendeMedDokdatoIForespørsel.dokumentDato shouldBe dokdatoUtgående2
        }
    }

    @Test
    fun `Skal oppdatere dokumentdato på notat`() {
        val originalDato = LocalDateTime.parse("2020-01-01T01:02:03")
        val oppdatertDato = LocalDateTime.parse("2022-01-05T01:02:03")
        val forsendelse = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                erNotat = true,
                dokumenter = listOf(
                    nyttDokument(
                        journalpostId = null,
                        dokumentreferanseOriginal = null,
                        rekkefølgeIndeks = 0,
                        dokumentDato = originalDato
                    )
                )
            )
        )

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!

        val oppdaterForespørsel = OppdaterForsendelseForespørsel(
            dokumentDato = oppdatertDato,
            dokumenter = listOf(
                OppdaterDokumentForespørsel(
                    tittel = "Ny tittel notat",
                    dokumentreferanse = hoveddokument.dokumentreferanse
                )
            )
        )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 1
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe "Ny tittel notat"
            oppdaterForespørsel.dokumentDato shouldBe oppdatertDato
        }
    }

    @Nested
    inner class OppdaterForsendelseErrorHandling {
        @Test
        fun `Skal feile hvis dokumentdato på notat settes fram i tid`() {
            val originalDato = LocalDateTime.parse("2020-01-01T01:02:03")
            val oppdatertDato = LocalDateTime.now().plusDays(1)
            val forsendelse = testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    erNotat = true,
                    dokumenter = listOf(
                        nyttDokument(
                            journalpostId = null,
                            dokumentreferanseOriginal = null,
                            rekkefølgeIndeks = 0,
                            dokumentDato = originalDato
                        )
                    )
                )
            )

            val hoveddokument = forsendelse.dokumenter.hoveddokument!!

            val oppdaterForespørsel = OppdaterForsendelseForespørsel(
                dokumentDato = oppdatertDato,
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel notat",
                        dokumentreferanse = hoveddokument.dokumentreferanse
                    )
                )
            )
            val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        @Test
        fun `Skal feile hvis det forsøkes å legge til ny dokument på notat forsendelse`() {
            val forsendelse = testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    erNotat = true,
                    dokumenter = listOf(
                        nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, dokumentMalId = DOKUMENTMAL_NOTAT)
                    )
                )
            )

            val hoveddokument = forsendelse.dokumenter.hoveddokument!!

            val oppdaterForespørsel = OppdaterForsendelseForespørsel(
                dokumenter = listOf(
                    OppdaterDokumentForespørsel(
                        tittel = "Ny tittel hoveddok",
                        dokumentreferanse = hoveddokument.dokumentreferanse
                    ),
                    OppdaterDokumentForespørsel(
                        tittel = "Ny dokument 1",
                        dokumentreferanse = "1123213213",
                        journalpostId = "JOARK-123123123"
                    )
                )
            )
            val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        @Test
        fun `Oppdater skal feile hvis ikke alle dokumenter er inkludert i forespørselen`() {
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
                        dokumentreferanse = "123133313123123123"
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
            val respons = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        @Test
        fun `Oppdater skal feile hvis samme dokumentent er inkludert flere ganger i forespørselen`() {
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
                        dokumentreferanse = hoveddokument.dokumentreferanse
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
            val respons = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        @Test
        fun `Skal ikke kunne legge til dokument på forsendelse med type notat`() {
            val forsendelse = testDataManager.opprettOgLagreForsendelse {
                er notat true
                +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
            }

            val forsendelseId = forsendelse.forsendelseId!!

            val opprettDokumentForespørsel = OpprettDokumentForespørsel(
                tittel = TITTEL_HOVEDDOKUMENT,
                dokumentmalId = DOKUMENTMAL_UTGÅENDE
            )

            val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
            responseNyDokument.statusCode shouldBe HttpStatus.BAD_REQUEST
            responseNyDokument.headers["Warning"]!![0] shouldBe "Forespørselen inneholder ugyldig data: Kan ikke legge til flere dokumenter til et notat"
        }

        @Test
        fun `Skal feile hvis eksisterende dokumentreferanse blir forsøkt lagt til på forsendelse`() {
            val dokument = nyttDokument()
            val forsendelse = testDataManager.opprettOgLagreForsendelse {
                +dokument
            }

            val forsendelseId = forsendelse.forsendelseId!!

            val opprettDokumentForespørsel = OpprettDokumentForespørsel(
                tittel = TITTEL_HOVEDDOKUMENT,
                dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                dokumentreferanse = dokument.dokumentreferanse,
                journalpostId = dokument.journalpostId
            )
            val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
            responseNyDokument.statusCode shouldBe HttpStatus.BAD_REQUEST
            responseNyDokument.headers["Warning"]!![0] shouldBe "Forespørselen inneholder ugyldig data: Forsendelse $forsendelseId har allerede tilknyttet dokument med dokumentreferanse ${dokument.dokumentreferanse}"
        }
    }
}
