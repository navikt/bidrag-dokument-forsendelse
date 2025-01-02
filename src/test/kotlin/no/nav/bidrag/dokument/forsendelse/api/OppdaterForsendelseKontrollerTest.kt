package no.nav.bidrag.dokument.forsendelse.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.Ordering
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.erStatiskDokument
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_NOTAT
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_STATISK_VEDLEGG
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_2
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_HOVEDDOKUMENT
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_1
import no.nav.bidrag.dokument.forsendelse.utils.TITTEL_VEDLEGG_2
import no.nav.bidrag.dokument.forsendelse.utils.er
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumentDato
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.vedlegger
import no.nav.bidrag.transport.dokument.DokumentHendelseType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime

class OppdaterForsendelseKontrollerTest : KontrollerTestRunner() {
    @Test
    fun `Skal oppdatere og endre rekkefølge på dokumentene i forsendelse`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(rekkefølgeIndeks = 1),
                            nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
        val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            tittel = vedlegg1.tittel,
                            dokumentreferanse = vedlegg1.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel hoveddok",
                            dokumentreferanse = hoveddokument.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = vedlegg2.tittel,
                            dokumentreferanse = vedlegg2.dokumentreferanse,
                        ),
                    ),
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
    fun `Skal oppdatere forsendelse som er under opprettelse`() {
        val mottakerId = "123123213213"
        val mottakerNavn = "Hans Navnsen"
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter = emptyList(),
                    status = ForsendelseStatus.UNDER_OPPRETTELSE,
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                mottaker =
                    MottakerTo(
                        ident = mottakerId,
                        navn = mottakerNavn,
                    ),
                tema = JournalTema.FAR,
                enhet = "4888",
                språk = "EN",
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            tittel = "Vedtak om barnebidrag",
                            dokumentmalId = DOKUMENTMAL_UTGÅENDE,
                        ),
                    ),
            )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.tittel shouldBe null
            oppdatertForsendelse.enhet shouldBe "4888"
            oppdatertForsendelse.gjelderIdent shouldBe forsendelse.gjelderIdent
            oppdatertForsendelse.status shouldBe ForsendelseStatus.UNDER_PRODUKSJON
            oppdatertForsendelse.mottaker!!.ident shouldBe mottakerId
            oppdatertForsendelse.mottaker!!.navn shouldBe mottakerNavn
            oppdatertForsendelse.mottaker!!.språk shouldBe "EN"
            oppdatertForsendelse.dokumenter.size shouldBe 1
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe "Vedtak om barnebidrag"
        }
    }

    @Test
    fun `Skal oppdatere forsendelse som er under opprettelse med annen gjelder`() {
        val mottakerNavn = "Hans Navnsen"
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter = emptyList(),
                    status = ForsendelseStatus.UNDER_OPPRETTELSE,
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!

        val gjelderIdent = "41242421421421"
        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                mottaker =
                    MottakerTo(
                        ident = gjelderIdent,
                        navn = mottakerNavn,
                    ),
                gjelderIdent = gjelderIdent,
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            tittel = "Vedtak om barnebidrag",
                            dokumentmalId = DOKUMENTMAL_UTGÅENDE,
                        ),
                    ),
            )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.gjelderIdent shouldBe gjelderIdent
        }
    }

    @Test
    fun `Skal oppdatere og opprette dokumenter i forsendelse`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = vedlegg1.dokumentreferanse,
                            fjernTilknytning = true,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel hoveddok",
                            dokumentreferanse = hoveddokument.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny dokument 1",
                            dokumentreferanse = "11232132313",
                            journalpostId = "JOARK-123123123",
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny dokument 2 bestilt",
                            dokumentmalId = DOKUMENTMAL_UTGÅENDE,
                        ),
                    ),
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
    fun `Skal oppdatere og opprette dokumenter via bestilling og kafka (bisys som produserer dokument`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                rekkefølgeIndeks = 0,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                            ),
                            nyttDokument(
                                journalpostId = "BID-123123213",
                                dokumentreferanseOriginal = "12312321333",
                                rekkefølgeIndeks = 1,
                                dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                            ),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!
        val vedlegg1 = forsendelse.dokumenter.vedlegger[0]

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = vedlegg1.dokumentreferanse,
                            fjernTilknytning = true,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel hoveddok",
                            dokumentreferanse = hoveddokument.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel dok bestilt med kafka melding",
                            dokumentmalId = DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel dok 2 bestilt med kafka melding",
                            dokumentmalId = DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny dokument 1 ekstern kilde",
                            dokumentreferanse = "11232132313",
                            journalpostId = "JOARK-123123123",
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny dokument 2 bestilt via bidrag-dokument-bestilling",
                            dokumentmalId = DOKUMENTMAL_UTGÅENDE,
                        ),
                    ),
            )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 5
            val dokumenter = oppdatertForsendelse.dokumenter.sortertEtterRekkefølge
            dokumenter[0].tittel shouldBe "Ny tittel hoveddok"
            dokumenter[1].tittel shouldBe "Ny tittel dok bestilt med kafka melding"
            dokumenter[2].tittel shouldBe "Ny tittel dok 2 bestilt med kafka melding"
            dokumenter[3].tittel shouldBe "Ny dokument 1 ekstern kilde"
            dokumenter[4].tittel shouldBe "Ny dokument 2 bestilt via bidrag-dokument-bestilling"

            dokumenter[0].dokumentmalId shouldBe HOVEDDOKUMENT_DOKUMENTMAL
            dokumenter[1].dokumentmalId shouldBe DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES
            dokumenter[2].dokumentmalId shouldBe DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2
            dokumenter[3].dokumentmalId shouldBe null
            dokumenter[4].dokumentmalId shouldBe DOKUMENTMAL_UTGÅENDE

            dokumenter[0].dokumentStatus shouldBe DokumentStatus.UNDER_REDIGERING
            dokumenter[1].dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            dokumenter[2].dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON
            dokumenter[3].dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
            dokumenter[4].dokumentStatus shouldBe DokumentStatus.UNDER_PRODUKSJON

            dokumenter[1].metadata.hentDokumentBestiltAntallGanger() shouldBe 1
            dokumenter[2].metadata.hentDokumentBestiltAntallGanger() shouldBe 1
            dokumenter[3].metadata.hentDokumentBestiltAntallGanger() shouldBe 0
            dokumenter[4].metadata.hentDokumentBestiltAntallGanger() shouldBe 1
            dokumenter[1].metadata.hentBestiltTidspunkt()!! shouldHaveSameDayAs LocalDateTime.now()
            dokumenter[2].metadata.hentBestiltTidspunkt()!! shouldHaveSameDayAs LocalDateTime.now()
            dokumenter[3].metadata.hentBestiltTidspunkt() shouldBe null
            dokumenter[4].metadata.hentBestiltTidspunkt()!! shouldHaveSameDayAs LocalDateTime.now()

            dokumenter[1].arkivsystem shouldBe DokumentArkivSystem.UKJENT
            dokumenter[2].arkivsystem shouldBe DokumentArkivSystem.UKJENT
            dokumenter[3].arkivsystem shouldBe DokumentArkivSystem.JOARK
            dokumenter[4].arkivsystem shouldBe DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER

            stubUtils.Valider().bestillDokumentKaltMed(DOKUMENTMAL_UTGÅENDE)
            stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES)
            stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2)
            verify(ordering = Ordering.SEQUENCE) {
                dokumentKafkaHendelseProdusent.publiser(
                    withArg {
                        it.forsendelseId shouldBe forsendelse.forsendelseId.toString()
                        it.hendelseType shouldBe DokumentHendelseType.BESTILLING
                        it.dokumentreferanse shouldBe dokumenter[1].dokumentreferanse
                    },
                )
                dokumentKafkaHendelseProdusent.publiser(
                    withArg {
                        it.forsendelseId shouldBe forsendelse.forsendelseId.toString()
                        it.hendelseType shouldBe DokumentHendelseType.BESTILLING
                        it.dokumentreferanse shouldBe dokumenter[2].dokumentreferanse
                    },
                )
            }
        }
    }

    @Test
    fun `Skal oppdatere og slette dokumentene i forsendelse`() {
        val forsendelse =
            testDataManager.opprettOgLagreForsendelse {
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

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = vedlegg3.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = vedlegg1.dokumentreferanse,
                            fjernTilknytning = true,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel hoveddok",
                            dokumentreferanse = hoveddokument.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = vedlegg2.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = vedlegg4.dokumentreferanse,
                            fjernTilknytning = true,
                        ),
                    ),
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
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(rekkefølgeIndeks = 1),
                            nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2),
                        ),
                ),
            )

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
        val forsendelse =
            testDataManager.opprettOgLagreForsendelse {
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
    fun `Skal legge til statisk vedlegg på forsendelse`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!

        val opprettDokumentForespørsel =
            OpprettDokumentForespørsel(
                tittel = TITTEL_VEDLEGG_1,
                dokumentmalId = DOKUMENTMAL_STATISK_VEDLEGG,
            )

        val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe TITTEL_HOVEDDOKUMENT
            oppdatertForsendelse.dokumenter.vedlegger[0].tittel shouldBe TITTEL_VEDLEGG_1
            oppdatertForsendelse.dokumenter.vedlegger[0].arkivsystem shouldBe DokumentArkivSystem.BIDRAG
            oppdatertForsendelse.dokumenter.vedlegger[0].dokumentStatus shouldBe DokumentStatus.FERDIGSTILT
            stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_STATISK_VEDLEGG)
        }
    }

    @Test
    fun `Skal legge til statisk vedlegg på forsendelse som er redigerbar skjema`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!

        val opprettDokumentForespørsel =
            OpprettDokumentForespørsel(
                tittel = TITTEL_VEDLEGG_1,
                dokumentmalId = DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR,
            )

        val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe TITTEL_HOVEDDOKUMENT
            oppdatertForsendelse.dokumenter.vedlegger[0].tittel shouldBe TITTEL_VEDLEGG_1
            oppdatertForsendelse.dokumenter.vedlegger[0].arkivsystem shouldBe DokumentArkivSystem.BIDRAG
            oppdatertForsendelse.dokumenter.vedlegger[0].dokumentStatus shouldBe DokumentStatus.MÅ_KONTROLLERES
            oppdatertForsendelse.dokumenter.vedlegger[0]
                .metadata
                .erSkjema() shouldBe true
            oppdatertForsendelse.dokumenter.vedlegger[0]
                .metadata
                .erStatiskDokument() shouldBe true
            stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR)
        }
    }

    @Test
    fun `Skal legge til og slette statisk dokument på forsendelse`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                rekkefølgeIndeks = 0,
                                tittel = "Tittel hoveddok",
                            ),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = hoveddokument.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = TITTEL_VEDLEGG_1,
                            dokumentmalId = DOKUMENTMAL_STATISK_VEDLEGG,
                        ),
                    ),
            )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        val dokumenter = oppdatertForsendelse.dokumenter.sortertEtterRekkefølge
        assertSoftly {
            dokumenter.size shouldBe 2

            dokumenter[0].tittel shouldBe "Tittel hoveddok"
            dokumenter[1].tittel shouldBe TITTEL_VEDLEGG_1
            dokumenter[1].erStatiskDokument() shouldBe true

            stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_STATISK_VEDLEGG)
        }

        val responsSlett =
            utførOppdaterForsendelseForespørsel(
                forsendelse.forsendelseIdMedPrefix,
                OppdaterForsendelseForespørsel(
                    dokumenter =
                        listOf(
                            OppdaterDokumentForespørsel(
                                dokumentreferanse = hoveddokument.dokumentreferanse,
                            ),
                            OppdaterDokumentForespørsel(
                                fjernTilknytning = true,
                                dokumentreferanse = dokumenter[1].dokumentreferanse,
                            ),
                        ),
                ),
            )
        responsSlett.statusCode shouldBe HttpStatus.OK
        val oppdatertForsendelseEtterSlett = testDataManager.hentForsendelse(forsendelseId)!!
        oppdatertForsendelseEtterSlett.dokumenter.size shouldBe 1
    }

    @Test
    fun `Skal oppdatere og legge til dokumenter på forsendelse`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                rekkefølgeIndeks = 0,
                                tittel = "Tittel hoveddok",
                            ),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = hoveddokument.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = TITTEL_VEDLEGG_1,
                            dokumentmalId = DOKUMENTMAL_STATISK_VEDLEGG,
                            språk = "DE",
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = TITTEL_VEDLEGG_2,
                            dokumentmalId = DOKUMENTMAL_UTGÅENDE,
                        ),
                    ),
            )
        val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
        respons.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 3

            val dokumenter = oppdatertForsendelse.dokumenter.sortertEtterRekkefølge
            dokumenter[0].tittel shouldBe "Tittel hoveddok"
            dokumenter[1].tittel shouldBe TITTEL_VEDLEGG_1
            dokumenter[1].språk shouldBe "DE"
            dokumenter[2].tittel shouldBe TITTEL_VEDLEGG_2
            dokumenter[2].språk shouldBe "NB"

            stubUtils.Valider().bestillDokumentKaltMed(DOKUMENTMAL_UTGÅENDE)
            stubUtils.Valider().bestillDokumentIkkeKalt(DOKUMENTMAL_STATISK_VEDLEGG)
        }
    }

    @Test
    fun `Skal legge til dokument på forsendelse`() {
        val forsendelse =
            testDataManager.opprettOgLagreForsendelse {
                +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
            }

        val forsendelseId = forsendelse.forsendelseId!!

        val opprettDokumentForespørsel =
            OpprettDokumentForespørsel(
                tittel = TITTEL_VEDLEGG_1,
                dokumentmalId = DOKUMENTMAL_UTGÅENDE_2,
                språk = "DE",
            )

        val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe TITTEL_HOVEDDOKUMENT
            oppdatertForsendelse.dokumenter.vedlegger[0].tittel shouldBe TITTEL_VEDLEGG_1
            oppdatertForsendelse.dokumenter.vedlegger[0].språk shouldBe "DE"
            stubUtils.Valider().bestillDokumentKaltMed(DOKUMENTMAL_UTGÅENDE_2)
        }
    }

    @Test
    fun `Skal legge til en ekstern dokument fra annen forsendelse`() {
        val originalForsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = "64443434",
                                dokumentreferanseOriginal = "12313213",
                                rekkefølgeIndeks = 1,
                                tittel = "Skjermet dokument",
                            ),
                        ),
                ),
            )
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                        ),
                ),
            )

        val originalDokument = originalForsendelse.dokumenter[1]
        val originalForsendelseId = originalForsendelse.forsendelseId!!
        val forsendelseId = forsendelse.forsendelseId!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = forsendelse.dokumenter[0].dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Tittel knyttet dokument",
                            journalpostId = "BIF-$originalForsendelseId",
                            dokumentreferanse = originalDokument.dokumentreferanse,
                        ),
                    ),
            )

        val responseNyDokument = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe TITTEL_HOVEDDOKUMENT
            val vedlegg = oppdatertForsendelse.dokumenter.vedlegger[0]
            vedlegg.tittel shouldBe "Tittel knyttet dokument"
            vedlegg.journalpostIdOriginal shouldBe originalDokument.journalpostIdOriginal
            vedlegg.dokumentreferanseOriginal shouldBe originalDokument.dokumentreferanseOriginal
        }
    }

    @Test
    fun `Skal lenke til dokument under redigering som tilhører annen forsendelse`() {
        val originalForsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = "64443434",
                                dokumentreferanseOriginal = "12313213",
                                rekkefølgeIndeks = 1,
                                tittel = "Skjermet dokument",
                            ),
                        ),
                ),
            )
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                        ),
                ),
            )

        val originalDokument = originalForsendelse.dokumenter[0]
        val originalDokumentMedReferanseTilJoark = originalForsendelse.dokumenter[1]
        val originalForsendelseId = originalForsendelse.forsendelseId!!
        val forsendelseId = forsendelse.forsendelseId!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = forsendelse.dokumenter[0].dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Tittel knyttet dokument",
                            journalpostId = "BIF-$originalForsendelseId",
                            dokumentreferanse = originalDokument.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Tittel dokument joark",
                            journalpostId = "BIF-$originalForsendelseId",
                            dokumentreferanse = originalDokumentMedReferanseTilJoark.dokumentreferanse,
                        ),
                    ),
            )

        val responseNyDokument = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 3
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe TITTEL_HOVEDDOKUMENT
            val vedlegg = oppdatertForsendelse.dokumenter.vedlegger[0]
            vedlegg.tittel shouldBe "Tittel knyttet dokument"
            vedlegg.journalpostIdOriginal shouldBe originalForsendelseId.toString()
            vedlegg.dokumentreferanseOriginal shouldBe originalDokument.dokumentreferanse

            val vedlegg2 = oppdatertForsendelse.dokumenter.vedlegger[1]
            vedlegg2.tittel shouldBe "Tittel dokument joark"
            vedlegg2.journalpostIdOriginal shouldBe "64443434"
            vedlegg2.dokumentreferanseOriginal shouldBe "12313213"
        }
    }

    @Test
    fun `Skal koble til dokument under redigering som er kobling til dokument i en annen forsendelse`() {
        val originalForsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = "64443434",
                                dokumentreferanseOriginal = "12313213",
                                rekkefølgeIndeks = 1,
                                tittel = "Skjermet dokument",
                            ),
                        ),
                ),
            )
        val originalDokument = originalForsendelse.dokumenter[0]
        val originalForsendelseId = originalForsendelse.forsendelseId!!

        val forsendelseMedKoblingTilOriginal =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = originalDokument.forsendelseId.toString(),
                                dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                                arkivsystem = DokumentArkivSystem.FORSENDELSE,
                                rekkefølgeIndeks = 0,
                            ),
                        ),
                ),
            )

        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                        ),
                ),
            )

        val kobleTilDokumentUnderRedigering = forsendelseMedKoblingTilOriginal.dokumenter[0]
        val kobleTilForsendelse = forsendelseMedKoblingTilOriginal.forsendelseId
        val forsendelseId = forsendelse.forsendelseId!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = forsendelse.dokumenter[0].dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Tittel knyttet dokument",
                            journalpostId = "BIF-$kobleTilForsendelse",
                            dokumentreferanse = kobleTilDokumentUnderRedigering.dokumentreferanse,
                        ),
                    ),
            )

        val responseNyDokument = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe TITTEL_HOVEDDOKUMENT
            val vedlegg = oppdatertForsendelse.dokumenter.vedlegger[0]
            vedlegg.tittel shouldBe "Tittel knyttet dokument"
            vedlegg.journalpostIdOriginal shouldBe originalForsendelseId.toString()
            vedlegg.dokumentreferanseOriginal shouldBe originalDokument.dokumentreferanse
        }
    }

    @Test
    fun `Skal feile dokument i forsendelse forsøkes å kobles til som lenke fra annen dokument`() {
        val originalForsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = "64443434",
                                dokumentreferanseOriginal = "12313213",
                                rekkefølgeIndeks = 1,
                                tittel = "Skjermet dokument",
                            ),
                        ),
                ),
            )
        val originalDokument = originalForsendelse.dokumenter[0]
        val originalForsendelseId = originalForsendelse.forsendelseId!!

        val forsendelseMedKoblingTilOriginal =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = originalForsendelseId.toString(),
                                dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                                arkivsystem = DokumentArkivSystem.FORSENDELSE,
                                rekkefølgeIndeks = 0,
                            ),
                        ),
                ),
            )

        val kobleTilDokument = forsendelseMedKoblingTilOriginal.dokumenter[0]
        val kobleTilForsendelse = forsendelseMedKoblingTilOriginal.forsendelseId
        val forsendelseId = originalForsendelse.forsendelseId!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = originalForsendelse.dokumenter[0].dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = originalForsendelse.dokumenter[1].dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Tittel knyttet dokument",
                            journalpostId = "BIF-$kobleTilForsendelse",
                            dokumentreferanse = kobleTilDokument.dokumentreferanse,
                        ),
                    ),
            )

        val responseNyDokument = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.BAD_REQUEST
        responseNyDokument.headers["Warning"]!![0] shouldBe "Dokument med tittel \"Tittel på hoveddokument\" er allerede lagt til i forsendelse. Kan ikke legge til samme dokument flere ganger"
    }

    @Test
    fun `Skal feile hvis samme dokument forsøkes å kobles til forsendelse`() {
        val originalForsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = "64443434",
                                dokumentreferanseOriginal = "12313213",
                                rekkefølgeIndeks = 1,
                                tittel = "Skjermet dokument",
                            ),
                        ),
                ),
            )
        val originalDokument = originalForsendelse.dokumenter[0]

        val forsendelseMedKoblingTilOriginal =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = originalDokument.forsendelseId.toString(),
                                dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                                rekkefølgeIndeks = 0,
                            ),
                        ),
                ),
            )

        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                        ),
                ),
            )

        val kobleTilDokument = forsendelseMedKoblingTilOriginal.dokumenter[0]
        val kobleTilForsendelse = forsendelseMedKoblingTilOriginal.forsendelseId
        val forsendelseId = forsendelse.forsendelseId!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = forsendelse.dokumenter[0].dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Tittel knyttet dokument",
                            journalpostId = "BIF-$kobleTilForsendelse",
                            dokumentreferanse = kobleTilDokument.dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            tittel = "Tittel knyttet dokument 2",
                            journalpostId = "BIF-${originalForsendelse.forsendelseId}",
                            dokumentreferanse = originalDokument.dokumentreferanse,
                        ),
                    ),
            )

        val responseNyDokument = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.BAD_REQUEST
        responseNyDokument.headers["Warning"]!![0] shouldContain "Kan ikke legge til samme dokument flere ganger til forsendelse"
    }

    @Test
    fun `Skal oppdatere kobling til dokument under produksjon som tilhører annen forsendelse hvis original dokument blir slettet`() {
        val originalForsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = "64443434",
                                dokumentreferanseOriginal = "12313213",
                                rekkefølgeIndeks = 1,
                                tittel = "Skjermet dokument",
                            ),
                        ),
                ),
            )

        val originalDokument = originalForsendelse.dokumenter[0]
        val originalForsendelseId = originalForsendelse.forsendelseId!!

        val forsendelseKoblet1 =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = originalForsendelseId.toString(),
                                dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                                rekkefølgeIndeks = 1,
                                tittel = "Skjermet dokument",
                            ),
                        ),
                ),
            )
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = originalForsendelseId.toString(),
                                dokumentreferanseOriginal = originalDokument.dokumentreferanse,
                                rekkefølgeIndeks = 1,
                                tittel = "Skjermet dokument",
                            ),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!
        val dokumentForsendelse1 = forsendelseKoblet1.dokumenter[1]

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = originalForsendelse.dokumenter[1].dokumentreferanse,
                        ),
                        OppdaterDokumentForespørsel(
                            dokumentreferanse = originalDokument.dokumentreferanse,
                            fjernTilknytning = true,
                        ),
                    ),
            )

        val responseNyDokument = utførOppdaterForsendelseForespørsel("BIF-$originalForsendelseId", oppdaterForespørsel)
        responseNyDokument.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelseId)!!

        assertSoftly {
            oppdatertForsendelse.dokumenter.size shouldBe 2
            oppdatertForsendelse.dokumenter.hoveddokument?.tittel shouldBe TITTEL_HOVEDDOKUMENT
            val vedlegg = oppdatertForsendelse.dokumenter.vedlegger[0]
            vedlegg.journalpostIdOriginal shouldBe forsendelseKoblet1.forsendelseId.toString()
            vedlegg.dokumentreferanseOriginal shouldBe dokumentForsendelse1.dokumentreferanse
        }
    }

    @Test
    fun `Skal ikke oppdatere dokumentdato på utgående`() {
        val dokdatoNotat = LocalDateTime.parse("2020-01-01T01:02:03")
        val dokdatoUtgående = LocalDateTime.parse("2021-01-01T01:02:03")
        val dokdatoUtgående2 = LocalDateTime.parse("2021-01-01T01:02:03")
        val dokdatoOppdater = LocalDateTime.parse("2022-01-05T01:02:03")

        val forsendelseNotat =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    erNotat = true,
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                rekkefølgeIndeks = 0,
                                dokumentDato = dokdatoNotat,
                            ),
                        ),
                ),
            )

        val forsendelseUtgående =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                rekkefølgeIndeks = 0,
                                dokumentDato = dokdatoUtgående,
                            ),
                        ),
                ),
            )

        val forsendelseUtgåendeMedDokdatoIForespørsel =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                rekkefølgeIndeks = 0,
                                dokumentDato = dokdatoUtgående2,
                            ),
                        ),
                ),
            )

        utførOppdaterForsendelseForespørsel(
            forsendelseNotat.forsendelseIdMedPrefix,
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel notat",
                            dokumentreferanse = forsendelseNotat.dokumenter[0].dokumentreferanse,
                        ),
                    ),
            ),
        ).statusCode shouldBe HttpStatus.OK

        utførOppdaterForsendelseForespørsel(
            forsendelseUtgående.forsendelseIdMedPrefix,
            OppdaterForsendelseForespørsel(
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel utgående",
                            dokumentreferanse = forsendelseUtgående.dokumenter[0].dokumentreferanse,
                        ),
                    ),
            ),
        ).statusCode shouldBe HttpStatus.OK

        utførOppdaterForsendelseForespørsel(
            forsendelseUtgåendeMedDokdatoIForespørsel.forsendelseIdMedPrefix,
            OppdaterForsendelseForespørsel(
                dokumentDato = dokdatoOppdater,
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel utgående",
                            dokumentreferanse = forsendelseUtgåendeMedDokdatoIForespørsel.dokumenter[0].dokumentreferanse,
                        ),
                    ),
            ),
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
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    erNotat = true,
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                rekkefølgeIndeks = 0,
                                dokumentDato = originalDato,
                            ),
                        ),
                ),
            )

        val forsendelseId = forsendelse.forsendelseId!!
        val hoveddokument = forsendelse.dokumenter.hoveddokument!!

        val oppdaterForespørsel =
            OppdaterForsendelseForespørsel(
                dokumentDato = oppdatertDato,
                dokumenter =
                    listOf(
                        OppdaterDokumentForespørsel(
                            tittel = "Ny tittel notat",
                            dokumentreferanse = hoveddokument.dokumentreferanse,
                        ),
                    ),
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
    inner class OppdaterForsendelseFeilhåndtering {
        @Test
        fun `Skal feile hvis samme dokument blir lagt til flere ganger fra ulike forsendelser`() {
            val originalForsendelse =
                testDataManager.lagreForsendelse(
                    opprettForsendelse2(
                        dokumenter =
                            listOf(
                                nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                                nyttDokument(
                                    journalpostId = "64443434",
                                    dokumentreferanseOriginal = "12313213",
                                    rekkefølgeIndeks = 1,
                                    tittel = "Skjermet dokument",
                                ),
                            ),
                    ),
                )
            val originalForsendelse2 =
                testDataManager.lagreForsendelse(
                    opprettForsendelse2(
                        dokumenter =
                            listOf(
                                nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                                nyttDokument(
                                    journalpostId = "64443434",
                                    dokumentreferanseOriginal = "12313213",
                                    rekkefølgeIndeks = 1,
                                    tittel = "Skjermet dokument",
                                ),
                            ),
                    ),
                )
            val forsendelse =
                testDataManager.lagreForsendelse(
                    opprettForsendelse2(
                        dokumenter =
                            listOf(
                                nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0),
                            ),
                    ),
                )

            val originalDokument = originalForsendelse.dokumenter[1]
            val originalDokument2 = originalForsendelse2.dokumenter[1]
            val originalForsendelseId = originalForsendelse.forsendelseId!!
            val originalForsendelseId2 = originalForsendelse2.forsendelseId!!
            val forsendelseId = forsendelse.forsendelseId!!

            val oppdaterForespørsel =
                OppdaterForsendelseForespørsel(
                    dokumenter =
                        listOf(
                            OppdaterDokumentForespørsel(
                                dokumentreferanse = forsendelse.dokumenter[0].dokumentreferanse,
                            ),
                            OppdaterDokumentForespørsel(
                                tittel = "Tittel knyttet dokument",
                                journalpostId = "BIF-$originalForsendelseId",
                                dokumentreferanse = originalDokument.dokumentreferanse,
                            ),
                            OppdaterDokumentForespørsel(
                                tittel = "Tittel knyttet dokument 2",
                                journalpostId = "BIF-$originalForsendelseId2",
                                dokumentreferanse = originalDokument2.dokumentreferanse,
                            ),
                        ),
                )

            val responseNyDokument = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
            responseNyDokument.statusCode shouldBe HttpStatus.BAD_REQUEST
            responseNyDokument.headers["Warning"]!![0] shouldContain "Kan ikke legge til samme dokument flere ganger til forsendelse"
        }

        @Test
        fun `Skal feile hvis dokumentdato på notat settes fram i tid`() {
            val originalDato = LocalDateTime.parse("2020-01-01T01:02:03")
            val oppdatertDato = LocalDateTime.now().plusDays(1)
            val forsendelse =
                testDataManager.lagreForsendelse(
                    opprettForsendelse2(
                        erNotat = true,
                        dokumenter =
                            listOf(
                                nyttDokument(
                                    journalpostId = null,
                                    dokumentreferanseOriginal = null,
                                    rekkefølgeIndeks = 0,
                                    dokumentDato = originalDato,
                                ),
                            ),
                    ),
                )

            val hoveddokument = forsendelse.dokumenter.hoveddokument!!

            val oppdaterForespørsel =
                OppdaterForsendelseForespørsel(
                    dokumentDato = oppdatertDato,
                    dokumenter =
                        listOf(
                            OppdaterDokumentForespørsel(
                                tittel = "Ny tittel notat",
                                dokumentreferanse = hoveddokument.dokumentreferanse,
                            ),
                        ),
                )
            val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        @Test
        fun `Skal feile hvis det forsøkes å legge til ny dokument på notat forsendelse`() {
            val forsendelse =
                testDataManager.lagreForsendelse(
                    opprettForsendelse2(
                        erNotat = true,
                        dokumenter =
                            listOf(
                                nyttDokument(
                                    journalpostId = null,
                                    dokumentreferanseOriginal = null,
                                    rekkefølgeIndeks = 0,
                                    dokumentMalId = DOKUMENTMAL_NOTAT,
                                ),
                            ),
                    ),
                )

            val hoveddokument = forsendelse.dokumenter.hoveddokument!!

            val oppdaterForespørsel =
                OppdaterForsendelseForespørsel(
                    dokumenter =
                        listOf(
                            OppdaterDokumentForespørsel(
                                tittel = "Ny tittel hoveddok",
                                dokumentreferanse = hoveddokument.dokumentreferanse,
                            ),
                            OppdaterDokumentForespørsel(
                                tittel = "Ny dokument 1",
                                dokumentreferanse = "1123213213",
                                journalpostId = "JOARK-123123123",
                            ),
                        ),
                )
            val respons = utførOppdaterForsendelseForespørsel(forsendelse.forsendelseIdMedPrefix, oppdaterForespørsel)
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        @Test
        fun `Oppdater skal feile hvis ikke alle dokumenter er inkludert i forespørselen`() {
            val forsendelse =
                testDataManager.opprettOgLagreForsendelse {
                    +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
                    +nyttDokument(rekkefølgeIndeks = 1)
                    +nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2)
                }

            val forsendelseId = forsendelse.forsendelseId!!
            val hoveddokument = forsendelse.dokumenter.hoveddokument!!
            val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
            val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

            val oppdaterForespørsel =
                OppdaterForsendelseForespørsel(
                    dokumenter =
                        listOf(
                            OppdaterDokumentForespørsel(
                                tittel = vedlegg1.tittel,
                                dokumentreferanse = "123133313123123123",
                            ),
                            OppdaterDokumentForespørsel(
                                tittel = "Ny tittel hoveddok",
                                dokumentreferanse = hoveddokument.dokumentreferanse,
                            ),
                            OppdaterDokumentForespørsel(
                                tittel = vedlegg2.tittel,
                                dokumentreferanse = vedlegg2.dokumentreferanse,
                            ),
                        ),
                )
            val respons = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        @Test
        fun `Oppdater skal feile hvis samme dokumentent er inkludert flere ganger i forespørselen`() {
            val forsendelse =
                testDataManager.opprettOgLagreForsendelse {
                    +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
                    +nyttDokument(rekkefølgeIndeks = 1)
                    +nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2)
                }

            val forsendelseId = forsendelse.forsendelseId!!
            val hoveddokument = forsendelse.dokumenter.hoveddokument!!
            val vedlegg1 = forsendelse.dokumenter.vedlegger[0]
            val vedlegg2 = forsendelse.dokumenter.vedlegger[1]

            val oppdaterForespørsel =
                OppdaterForsendelseForespørsel(
                    dokumenter =
                        listOf(
                            OppdaterDokumentForespørsel(
                                tittel = vedlegg1.tittel,
                                dokumentreferanse = hoveddokument.dokumentreferanse,
                            ),
                            OppdaterDokumentForespørsel(
                                tittel = "Ny tittel hoveddok",
                                dokumentreferanse = hoveddokument.dokumentreferanse,
                            ),
                            OppdaterDokumentForespørsel(
                                tittel = vedlegg2.tittel,
                                dokumentreferanse = vedlegg2.dokumentreferanse,
                            ),
                        ),
                )
            val respons = utførOppdaterForsendelseForespørsel("BIF-$forsendelseId", oppdaterForespørsel)
            respons.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        @Test
        fun `Skal ikke kunne legge til dokument på forsendelse med type notat`() {
            val forsendelse =
                testDataManager.opprettOgLagreForsendelse {
                    er notat true
                    +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0)
                }

            val forsendelseId = forsendelse.forsendelseId!!

            val opprettDokumentForespørsel =
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = DOKUMENTMAL_UTGÅENDE,
                )

            val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
            responseNyDokument.statusCode shouldBe HttpStatus.BAD_REQUEST
            responseNyDokument.headers["Warning"]!![0] shouldBe "Forespørselen inneholder ugyldig data: Kan ikke legge til flere dokumenter til et notat"
        }

        @Test
        fun `Skal feile hvis eksisterende dokumentreferanse blir forsøkt lagt til på forsendelse`() {
            val dokument = nyttDokument()
            val forsendelse =
                testDataManager.opprettOgLagreForsendelse {
                    +dokument
                }

            val forsendelseId = forsendelse.forsendelseId!!

            val opprettDokumentForespørsel =
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    dokumentreferanse = dokument.dokumentreferanse,
                    journalpostId = dokument.journalpostId,
                )
            val responseNyDokument = utførLeggTilDokumentForespørsel(forsendelseId, opprettDokumentForespørsel)
            responseNyDokument.statusCode shouldBe HttpStatus.BAD_REQUEST
            responseNyDokument.headers["Warning"]!![0] shouldBe "Forespørselen inneholder ugyldig data: Forsendelse $forsendelseId har allerede tilknyttet dokument med dokumentreferanse ${dokument.dokumentreferanse}"
        }
    }
}
