package no.nav.bidrag.dokument.forsendelse.api

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.verify
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.DokumentMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.opprettReferanseId
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_STATISK_VEDLEGG
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENTMAL_UTGÅENDE
import no.nav.bidrag.dokument.forsendelse.utils.HOVEDDOKUMENT_DOKUMENTMAL
import no.nav.bidrag.dokument.forsendelse.utils.SAKSBEHANDLER_IDENT
import no.nav.bidrag.dokument.forsendelse.utils.med
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.transport.dokument.DistribuerJournalpostRequest
import no.nav.bidrag.transport.dokument.DistribuerJournalpostResponse
import no.nav.bidrag.transport.dokument.OpprettDokumentDto
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime

class DistribuerKontrollerTest : KontrollerTestRunner() {
    @MockkBean
    lateinit var gcpCloudStorage: GcpCloudStorage

    protected fun utførHentKanDistribuere(forsendelseId: String): ResponseEntity<String> {
        return httpHeaderTestRestTemplate.getForEntity<String>("${rootUri()}/journal/distribuer/$forsendelseId/enabled")
    }

    protected fun utførDistribuerForsendelse(
        forsendelseId: String,
        forespørsel: DistribuerJournalpostRequest? = null,
        batchId: String? = null,
        ingenDistribusjon: Boolean? = false,
    ): ResponseEntity<DistribuerJournalpostResponse> {
        val url = UriComponentsBuilder.fromUriString("${rootUri()}/journal/distribuer/$forsendelseId")
        batchId?.let { url.queryParam("batchId", it) }
        ingenDistribusjon?.let { url.queryParam("ingenDistribusjon", it) }
        return httpHeaderTestRestTemplate.postForEntity<DistribuerJournalpostResponse>(
            url.toUriString(),
            forespørsel?.let { HttpEntity(it) },
        )
    }

    @Test
    fun `skal returnere at forsendelse kan distribueres hvis forsendelse er ferdigstilt`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT),
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                            ),
                        ),
                ),
            )
        val response = utførHentKanDistribuere(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK
    }

    @Test
    fun `skal returnere at forsendelse ikke kan distribueres hvis forsendelse er inneholder dokumenter som ikke er ferdigstilt`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                            ),
                            nyttDokument(
                                journalpostId = "123213213",
                                dokumentreferanseOriginal = "123213213",
                                dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                                rekkefølgeIndeks = 1,
                            ),
                        ),
                ),
            )

        val response = utførHentKanDistribuere(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.NOT_ACCEPTABLE
    }

    @Test
    fun `skal feile distribusjon hvis forsendelse er inneholder dokumenter som ikke er ferdigstilt`() {
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                rekkefølgeIndeks = 0,
                            ),
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                                rekkefølgeIndeks = 1,
                            ),
                            nyttDokument(
                                journalpostId = "13213123",
                                dokumentreferanseOriginal = "123213123213",
                                dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                                rekkefølgeIndeks = 2,
                            ),
                        ),
                ),
            )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.headers["Warning"]!![0] shouldContain "Alle dokumenter er ikke ferdigstilt"
    }

    @Test
    fun `skal ikke distribuere forsendelse hvis et av dokumentene har tittel med ugyldig tegn`() {
        val forsendelse =
            testDataManager.opprettOgLagreForsendelse {
                +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
                +nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "\u001A Tittel test \u001A",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1,
                )
            }

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.BAD_REQUEST

        response.headers["Warning"]?.get(0) shouldBe "Forsendelsen kan ikke ferdigstilles: Dokument med tittel   Tittel test   " +
            "og dokumentreferanse ${forsendelse.dokumenter[1].dokumentreferanse} " +
            "i forsendelse ${forsendelse.forsendelseId} inneholder ugyldig tegn"
    }

    @Test
    fun `skal distribuere forsendelse med tema FAR`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse =
            testDataManager.opprettOgLagreForsendelse {
                med tema ForsendelseTema.FAR
                +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
                +nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1,
                )
            }

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") },
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
        val referanseId = oppdatertForsendelse.opprettReferanseId()

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }
            @Language("Json")
            val expectedJson =
                """
                {
                    "skalFerdigstilles":true,
                    "tittel":"Tittel på hoveddokument",
                    "gjelderIdent":"${forsendelse.gjelderIdent}",
                    "avsenderMottaker":{"navn":"${forsendelse.mottaker?.navn}","ident":"${forsendelse.mottaker?.ident}","type":"FNR","adresse":null},
                    "dokumenter":[
                        {"tittel":"Tittel på hoveddokument","brevkode":"BI091","dokumentmalId":"BI091","dokumentreferanse":"${forsendelse.dokumenter[0].dokumentreferanse}"},
                        {"tittel":"Tittel vedlegg","brevkode":"BI100","dokumentmalId":"BI100","dokumentreferanse":"${forsendelse.dokumenter[1].dokumentreferanse}"}
                    ],
                    "tilknyttSaker":["${forsendelse.saksnummer}"],
                    "tema":"FAR",
                    "journalposttype":"UTGÅENDE",
                    "referanseId":"$referanseId",
                    "journalførendeEnhet":"${forsendelse.enhet}"
                }
                """.trimIndent().replace("\n", "").replace("  ", "")
            stubUtils.Valider().opprettJournalpostKaltMed(expectedJson)
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
        }
    }

    @Test
    fun `skal distribuere forsendelse`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse =
            testDataManager.opprettOgLagreForsendelse {
                +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
                +nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1,
                )
            }

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") },
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        val referanseId = oppdatertForsendelse.opprettReferanseId()
        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT
            oppdatertForsendelse.referanseId shouldBe referanseId

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }
            @Language("Json")
            val expectedJson =
                """
                {
                    "skalFerdigstilles":true,
                    "tittel":"Tittel på hoveddokument",
                    "gjelderIdent":"${forsendelse.gjelderIdent}",
                    "avsenderMottaker":{"navn":"${forsendelse.mottaker?.navn}","ident":"${forsendelse.mottaker?.ident}","type":"FNR","adresse":null},
                    "dokumenter":[
                        {"tittel":"Tittel på hoveddokument","brevkode":"BI091","dokumentmalId":"BI091","dokumentreferanse":"${forsendelse.dokumenter[0].dokumentreferanse}"},
                        {"tittel":"Tittel vedlegg","brevkode":"BI100","dokumentmalId":"BI100","dokumentreferanse":"${forsendelse.dokumenter[1].dokumentreferanse}"}
                    ],
                    "tilknyttSaker":["${forsendelse.saksnummer}"],
                    "tema":"BID",
                    "journalposttype":"UTGÅENDE",
                    "referanseId":"$referanseId",
                    "journalførendeEnhet":"${forsendelse.enhet}"
                }
                """.trimIndent().replace("\n", "").replace("  ", "")

            stubUtils.Valider().opprettJournalpostKaltMed(expectedJson)
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
        }

        verify {
            forsendelseHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelse.forsendelseId
                },
            )
        }
    }

    @Test
    fun `skal distribuere forsendelse med dokumenter knyttet til andre forsendelser`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelseOriginal =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                tittel = "Dokument som det knyttes til 1",
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                                rekkefølgeIndeks = 0,
                            ),
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                tittel = "Dokument som det knyttes til 2",
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                                rekkefølgeIndeks = 1,
                            ),
                        ),
                ),
            )
        val dokumentKnyttetTil1 = forsendelseOriginal.dokumenter[0]
        val dokumentKnyttetTil2 = forsendelseOriginal.dokumenter[1]
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = dokumentKnyttetTil1.forsendelseId.toString(),
                                dokumentreferanseOriginal = dokumentKnyttetTil1.dokumentreferanse,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                arkivsystem = DokumentArkivSystem.FORSENDELSE,
                                tittel = "Dokument knyttet til forsendelse 1",
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                                rekkefølgeIndeks = 0,
                            ),
                            nyttDokument(
                                journalpostId = dokumentKnyttetTil2.forsendelseId.toString(),
                                dokumentreferanseOriginal = dokumentKnyttetTil2.dokumentreferanse,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                arkivsystem = DokumentArkivSystem.FORSENDELSE,
                                tittel = "Dokument knyttet til forsendelse 2",
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                                rekkefølgeIndeks = 1,
                            ),
                        ),
                ),
            )
        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") },
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
        val referanseId = oppdatertForsendelse.opprettReferanseId()

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }
            @Language("Json")
            val expectedJson =
                """
                {
                    "skalFerdigstilles":true,
                    "tittel":"Dokument knyttet til forsendelse 1",
                    "gjelderIdent":"${forsendelse.gjelderIdent}",
                    "avsenderMottaker":{"navn":"${forsendelse.mottaker?.navn}","ident":"${forsendelse.mottaker?.ident}","type":"FNR","adresse":null},
                    "dokumenter":[
                        {"tittel":"Dokument knyttet til forsendelse 1","brevkode":"$DOKUMENTMAL_UTGÅENDE","dokumentmalId":"$DOKUMENTMAL_UTGÅENDE","dokumentreferanse":"${forsendelse.dokumenter[0].dokumentreferanse}"},
                        {"tittel":"Dokument knyttet til forsendelse 2","brevkode":"$DOKUMENTMAL_UTGÅENDE","dokumentmalId":"$DOKUMENTMAL_UTGÅENDE","dokumentreferanse":"${forsendelse.dokumenter[1].dokumentreferanse}"}
                    ],
                    "tilknyttSaker":["${forsendelse.saksnummer}"],
                    "tema":"BID",
                    "journalposttype":"UTGÅENDE",
                    "referanseId":"$referanseId",
                    "journalførendeEnhet":"${forsendelse.enhet}"
                }
                """.trimIndent().replace("\n", "").replace("  ", "")
            stubUtils.Valider().opprettJournalpostKaltMed(expectedJson)
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
            verify {
                forsendelseHendelseProdusent.publiserForsendelse(
                    withArg {
                        it.forsendelseId shouldBe forsendelse.forsendelseId
                    },
                )
            }
        }
    }

    @Test
    fun `skal distribuere forsendelse med statiske vedlegg`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                tittel = "Statisk vedlegg",
                                dokumentMalId = DOKUMENTMAL_STATISK_VEDLEGG,
                                rekkefølgeIndeks = 0,
                                metadata =
                                    run {
                                        val metadata = DokumentMetadataDo()
                                        metadata.markerSomStatiskDokument()
                                        metadata
                                    },
                            ),
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                tittel = "Dokument knyttet til forsendelse 1",
                                dokumentMalId = DOKUMENTMAL_UTGÅENDE,
                                rekkefølgeIndeks = 1,
                            ),
                        ),
                ),
            )

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") },
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
        val referanseId = oppdatertForsendelse.opprettReferanseId()

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }
            @Language("Json")
            val expectedJson =
                """
                {
                    "skalFerdigstilles":true,
                    "tittel":"Statisk vedlegg",
                    "gjelderIdent":"${forsendelse.gjelderIdent}",
                    "avsenderMottaker":{"navn":"${forsendelse.mottaker?.navn}","ident":"${forsendelse.mottaker?.ident}","type":"FNR","adresse":null},
                    "dokumenter":[
                        {"tittel":"Statisk vedlegg","brevkode":"$DOKUMENTMAL_STATISK_VEDLEGG","dokumentmalId":"$DOKUMENTMAL_STATISK_VEDLEGG","dokumentreferanse":"${forsendelse.dokumenter[0].dokumentreferanse}"},
                        {"tittel":"Dokument knyttet til forsendelse 1","brevkode":"$DOKUMENTMAL_UTGÅENDE","dokumentmalId":"$DOKUMENTMAL_UTGÅENDE","dokumentreferanse":"${forsendelse.dokumenter[1].dokumentreferanse}"}
                    ],
                    "tilknyttSaker":["${forsendelse.saksnummer}"],
                    "tema":"BID",
                    "journalposttype":"UTGÅENDE",
                    "referanseId":"$referanseId",
                    "journalførendeEnhet":"${forsendelse.enhet}"
                }
                """.trimIndent().replace("\n", "").replace("  ", "")
            stubUtils.Valider().opprettJournalpostKaltMed(expectedJson)
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId")
            verify {
                forsendelseHendelseProdusent.publiserForsendelse(
                    withArg {
                        it.forsendelseId shouldBe forsendelse.forsendelseId
                    },
                )
            }
        }
    }

    @Test
    fun `skal ikke distribuere hvis allerede distribuert`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    status = ForsendelseStatus.DISTRIBUERT,
                    arkivJournalpostId = nyJournalpostId,
                    distribusjonBestillingsId = bestillingId,
                    dokumenter = listOf(nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)),
                ),
            )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!

        assertSoftly {
            response.body!!.journalpostId shouldBe nyJournalpostId
            response.body!!.bestillingsId shouldBe bestillingId
            stubUtils.Valider().opprettJournalpostIkkeKalt()
            stubUtils.Valider().bestillDokumentIkkeKalt(HOVEDDOKUMENT_DOKUMENTMAL)
        }

        verify(exactly = 0) {
            forsendelseHendelseProdusent.publiserForsendelse(
                withArg {
                    it.forsendelseId shouldBe forsendelse.forsendelseId
                },
            )
        }
    }

    @Test
    fun `skal distribuere forsendelse med batchId`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        val batchId = "FB050"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse =
            testDataManager.opprettOgLagreForsendelse {
                +nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0)
                +nyttDokument(
                    journalpostId = null,
                    dokumentreferanseOriginal = null,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    tittel = "Tittel vedlegg",
                    dokumentMalId = "BI100",
                    rekkefølgeIndeks = 1,
                )
            }

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") },
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix, batchId = batchId)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
        val referanseId = oppdatertForsendelse.opprettReferanseId()

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe bestillingId
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.batchId shouldBe batchId
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }

            @Language("Json")
            val expectedJson =
                """
                {
                    "skalFerdigstilles":true,
                    "tittel":"Tittel på hoveddokument",
                    "gjelderIdent":"${forsendelse.gjelderIdent}",
                    "avsenderMottaker":{"navn":"${forsendelse.mottaker?.navn}","ident":"${forsendelse.mottaker?.ident}","type":"FNR","adresse":null},
                    "dokumenter":[
                        {"tittel":"Tittel på hoveddokument","brevkode":"BI091","dokumentmalId":"BI091","dokumentreferanse":"${forsendelse.dokumenter[0].dokumentreferanse}"},
                        {"tittel":"Tittel vedlegg","brevkode":"BI100","dokumentmalId":"BI100","dokumentreferanse":"${forsendelse.dokumenter[1].dokumentreferanse}"}
                    ],
                    "tilknyttSaker":["${forsendelse.saksnummer}"],
                    "tema":"BID",
                    "journalposttype":"UTGÅENDE",
                    "referanseId":"$referanseId",
                    "journalførendeEnhet":"${forsendelse.enhet}"
                }
                """.trimIndent().replace("\n", "").replace("  ", "")
            stubUtils.Valider().opprettJournalpostKaltMed(expectedJson)
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId", batchId = batchId)

            verify {
                forsendelseHendelseProdusent.publiserForsendelse(
                    withArg {
                        it.forsendelseId shouldBe forsendelse.forsendelseId
                    },
                )
            }
        }
    }

    @Test
    fun `skal distribuere forsendelse lokalt`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                tittel = "Tittel vedlegg",
                                dokumentMalId = "BI100",
                                rekkefølgeIndeks = 1,
                            ),
                        ),
                ),
            )

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") },
        )

        val response = utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix, DistribuerJournalpostRequest(lokalUtskrift = true))

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
        val referanseId = oppdatertForsendelse.opprettReferanseId()

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe null
            oppdatertForsendelse.distribusjonKanal shouldBe DistribusjonKanal.LOKAL_UTSKRIFT
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT_LOKALT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }

            @Language("Json")
            val expectedJson =
                """
                {
                    "skalFerdigstilles":true,
                    "tittel":"Tittel på hoveddokument",
                    "gjelderIdent":"${forsendelse.gjelderIdent}",
                    "avsenderMottaker":{"navn":"${forsendelse.mottaker?.navn}","ident":"${forsendelse.mottaker?.ident}","type":"FNR","adresse":null},
                    "dokumenter":[
                        {"tittel":"Tittel på hoveddokument (dokumentet er sendt per post med vedlegg)","brevkode":"BI091","dokumentmalId":"BI091","dokumentreferanse":"${forsendelse.dokumenter[0].dokumentreferanse}"},
                        {"tittel":"Tittel vedlegg","brevkode":"BI100","dokumentmalId":"BI100","dokumentreferanse":"${forsendelse.dokumenter[1].dokumentreferanse}"}
                    ],
                    "tilknyttSaker":["${forsendelse.saksnummer}"],
                    "kanal":"LOKAL_UTSKRIFT",
                    "tema":"BID",
                    "journalposttype":"UTGÅENDE",
                    "referanseId":"$referanseId",
                    "journalførendeEnhet":"${forsendelse.enhet}"
                }
                """.trimIndent().replace("\n", "").replace("  ", "")
            stubUtils.Valider().opprettJournalpostKaltMed(expectedJson)
            stubUtils.Valider().bestillDistribusjonKaltMed("JOARK-$nyJournalpostId", "\"lokalUtskrift\":true")

            verify {
                forsendelseHendelseProdusent.publiserForsendelse(
                    withArg {
                        it.forsendelseId shouldBe forsendelse.forsendelseId
                    },
                )
            }
        }
    }

    @Test
    fun `skal distribuere forsendelse som ingen distribusjon`() {
        val bestillingId = "asdasdasd-asd213123-adsda231231231-ada"
        val nyJournalpostId = "21313331231"
        stubUtils.stubHentDokument()
        stubUtils.stubBestillDistribusjon(bestillingId)
        val forsendelse =
            testDataManager.lagreForsendelse(
                opprettForsendelse2(
                    dokumenter =
                        listOf(
                            nyttDokument(dokumentStatus = DokumentStatus.FERDIGSTILT, rekkefølgeIndeks = 0),
                            nyttDokument(
                                journalpostId = null,
                                dokumentreferanseOriginal = null,
                                dokumentStatus = DokumentStatus.FERDIGSTILT,
                                tittel = "Tittel vedlegg",
                                dokumentMalId = "BI100",
                                rekkefølgeIndeks = 1,
                            ),
                        ),
                ),
            )

        stubUtils.stubOpprettJournalpost(
            nyJournalpostId,
            forsendelse.dokumenter.map { OpprettDokumentDto(it.tittel, dokumentreferanse = "JOARK${it.dokumentreferanse}") },
        )

        val response =
            utførDistribuerForsendelse(forsendelse.forsendelseIdMedPrefix, DistribuerJournalpostRequest(), ingenDistribusjon = true)

        response.statusCode shouldBe HttpStatus.OK

        val oppdatertForsendelse = testDataManager.hentForsendelse(forsendelse.forsendelseId!!)!!
        val referanseId = oppdatertForsendelse.opprettReferanseId()

        assertSoftly {
            oppdatertForsendelse.distribusjonBestillingsId shouldBe null
            oppdatertForsendelse.distribusjonKanal shouldBe DistribusjonKanal.INGEN_DISTRIBUSJON
            oppdatertForsendelse.distribuertTidspunkt!! shouldHaveSameDayAs LocalDateTime.now()
            oppdatertForsendelse.distribuertAvIdent shouldBe SAKSBEHANDLER_IDENT
            oppdatertForsendelse.status shouldBe ForsendelseStatus.DISTRIBUERT

            oppdatertForsendelse.dokumenter.forEach {
                it.dokumentreferanseFagarkiv shouldBe "JOARK${it.dokumentreferanse}"
            }

            @Language("Json")
            val expectedJson =
                """
                {
                    "skalFerdigstilles":true,
                    "tittel":"Tittel på hoveddokument",
                    "gjelderIdent":"${forsendelse.gjelderIdent}",
                    "avsenderMottaker":{"navn":"${forsendelse.mottaker?.navn}","ident":"${forsendelse.mottaker?.ident}","type":"FNR","adresse":null},
                    "dokumenter":[
                        {"tittel":"Tittel på hoveddokument","brevkode":"BI091","dokumentmalId":"BI091","dokumentreferanse":"${forsendelse.dokumenter[0].dokumentreferanse}"},
                        {"tittel":"Tittel vedlegg","brevkode":"BI100","dokumentmalId":"BI100","dokumentreferanse":"${forsendelse.dokumenter[1].dokumentreferanse}"}
                    ],
                    "tilknyttSaker":["${forsendelse.saksnummer}"],
                    "kanal":"INGEN_DISTRIBUSJON",
                    "tema":"BID",
                    "journalposttype":"UTGÅENDE",
                    "referanseId":"$referanseId",
                    "journalførendeEnhet":"${forsendelse.enhet}"
                }
                """.trimIndent().replace("\n", "").replace("  ", "")
            stubUtils.Valider().opprettJournalpostKaltMed(expectedJson)
            stubUtils.Valider().bestillDistribusjonIkkeKalt("JOARK-$nyJournalpostId")

            verify {
                forsendelseHendelseProdusent.publiserForsendelse(
                    withArg {
                        it.forsendelseId shouldBe forsendelse.forsendelseId
                    },
                )
            }
        }
    }
}
