package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.mapper.tilJournalpostDto
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import no.nav.bidrag.dokument.forsendelse.utils.opprettForsendelse
import org.junit.jupiter.api.Test

class ForsendelseMapperTest {

    @Test
    fun `skal mappe til journalpostDto`() {
        val forsendelse = opprettForsendelse {
            +nyttDokument(journalpostId = null, dokumentreferanseOriginal = null, rekkefølgeIndeks = 0, tittel = "HOVEDDOK")
            +nyttDokument(rekkefølgeIndeks = 1, tittel = "VEDLEGG1")
            +nyttDokument(rekkefølgeIndeks = 3, slettet = true, tittel = "VEDLEGG3")
            +nyttDokument(journalpostId = "BID-123123213", dokumentreferanseOriginal = "12312321333", rekkefølgeIndeks = 2, tittel = "VEDLEGG2")
        }

        val journalpostDto = forsendelse.tilJournalpostDto()
    }
}