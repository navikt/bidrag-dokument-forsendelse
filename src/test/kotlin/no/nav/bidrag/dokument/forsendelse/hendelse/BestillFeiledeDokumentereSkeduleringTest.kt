package no.nav.bidrag.dokument.forsendelse.hendelse

import no.nav.bidrag.dokument.forsendelse.TestContainerRunner
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse2
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BestillFeiledeDokumentereSkeduleringTest : TestContainerRunner() {
    @Autowired
    private lateinit var skedulering: DokumentSkedulering

    @BeforeEach
    fun setupMocks() {
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubBestillDokument()
        stubUtils.stubBestillDokumenDetaljer()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
    }

    @Test
    fun `Skal oppdatere status på dokument til UNDER_REDIGERING ved mottatt hendelse`() {
        val forsendelse1 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.UKJENT,
                        dokumentMalId = "MAL1"
                    )
                )
            )
        )
        val forsendelse2 = testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.UKJENT,
                        dokumentMalId = "MAL2"
                    )
                )
            )
        )
        testDataManager.lagreForsendelse(
            opprettForsendelse2(
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.UNDER_REDIGERING,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.UKJENT,
                        dokumentMalId = "MAL3"
                    )
                )
            )
        )

        skedulering.bestillFeiledeDokumenterPåNytt()

        stubUtils.Valider().bestillDokumentKaltMed(
            "MAL1",
            "\"saksbehandler\":{\"ident\":\"Z999444\",\"navn\":null}",
            "\"dokumentreferanse\":\"${forsendelse1.dokumenter.hoveddokument!!.dokumentreferanse}\""
        )
        stubUtils.Valider().bestillDokumentKaltMed(
            "MAL2",
            "\"saksbehandler\":{\"ident\":\"Z999444\",\"navn\":null}",
            "\"dokumentreferanse\":\"${forsendelse2.dokumenter.hoveddokument!!.dokumentreferanse}\""
        )
        stubUtils.Valider().bestillDokumentIkkeKalt("MAL3")
    }

    @Test
    fun `Skal ignorere dokumenter hvor forsendelse ikke har status UNDER_PRODUKSJON`() {
        testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.SLETTET,
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.UKJENT,
                        dokumentMalId = "MAL1"
                    )
                )
            )
        )
        testDataManager.lagreForsendelse(
            opprettForsendelse2(
                status = ForsendelseStatus.SLETTET,
                dokumenter = listOf(
                    nyttDokument(
                        dokumentreferanseOriginal = null,
                        journalpostId = null,
                        dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                        tittel = "FORSENDELSE 1",
                        arkivsystem = DokumentArkivSystem.UKJENT,
                        dokumentMalId = "MAL2"
                    )
                )
            )
        )

        skedulering.bestillFeiledeDokumenterPåNytt()

        stubUtils.Valider().bestillDokumentIkkeKalt("MAL1")
        stubUtils.Valider().bestillDokumentIkkeKalt("MAL2")
    }
}
