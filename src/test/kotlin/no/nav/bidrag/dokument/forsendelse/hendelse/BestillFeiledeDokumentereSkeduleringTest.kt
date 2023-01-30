package no.nav.bidrag.dokument.forsendelse.hendelse

import no.nav.bidrag.dokument.forsendelse.CommonTestRunner
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BestillFeiledeDokumentereSkeduleringTest : CommonTestRunner() {
    @Autowired
    private lateinit var bestillingLytter: DokumentBestillingLytter

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
        testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentreferanseOriginal = null, journalpostId = null, dokumentStatus = DokumentStatus.BESTILLING_FEILET, tittel = "FORSENDELSE 1", arkivsystem = DokumentArkivSystem.UKJENT, dokumentMalId = "MAL1")
        }
        testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentreferanseOriginal = null, journalpostId = null, dokumentStatus = DokumentStatus.BESTILLING_FEILET, tittel = "FORSENDELSE 1", arkivsystem = DokumentArkivSystem.UKJENT, dokumentMalId = "MAL2")
        }

        testDataManager.opprettOgLagreForsendelse {
            +nyttDokument(dokumentreferanseOriginal = null, journalpostId = null, dokumentStatus = DokumentStatus.UNDER_REDIGERING, tittel = "FORSENDELSE 1", arkivsystem = DokumentArkivSystem.UKJENT, dokumentMalId = "MAL2")
        }

        bestillingLytter.bestillFeiledeDokumenterPåNytt()

        stubUtils.Valider().bestillDokumentKaltMed("MAL1", "\"saksbehandler\":{\"ident\":\"Z999444\",\"navn\":null}")
        stubUtils.Valider().bestillDokumentKaltMed("MAL2", "\"saksbehandler\":{\"ident\":\"Z999444\",\"navn\":null}")
        stubUtils.Valider().bestillDokumentIkkeKalt("MAL3")

    }
}