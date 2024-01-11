package no.nav.bidrag.dokument.forsendelse.utvidelser

import io.kotest.matchers.shouldBe
import no.nav.bidrag.dokument.forsendelse.utils.nyttDokument
import org.junit.jupiter.api.Test

class ForsendelseUtvidelserTest {
    @Test
    fun `Skal returnere dokumenter med hoveddokument først og vedleggene sortert rekkefølge`() {
        val hoveddokument = nyttDokument(tittel = "HOVEDDOK")
        val vedlegg1 = nyttDokument(rekkefølgeIndeks = 1, tittel = "VEDLEGG1")
        val vedlegg2 = nyttDokument(rekkefølgeIndeks = 2, slettet = true, tittel = "VEDLEGG2")
        val vedlegg3 = nyttDokument(rekkefølgeIndeks = 3, tittel = "VEDLEGG3")
        val dokumenter = listOf(hoveddokument, vedlegg1, vedlegg2, vedlegg3)

        val sortertDokumenter = dokumenter.sortertEtterRekkefølge

        sortertDokumenter[0].tittel shouldBe "HOVEDDOK"
        sortertDokumenter[1].tittel shouldBe "VEDLEGG1"
        sortertDokumenter[2].tittel shouldBe "VEDLEGG3"
        sortertDokumenter[3].tittel shouldBe "VEDLEGG2"

        sortertDokumenter[0].rekkefølgeIndeks shouldBe 0
        sortertDokumenter[1].rekkefølgeIndeks shouldBe 1
        sortertDokumenter[2].rekkefølgeIndeks shouldBe 2
        sortertDokumenter[3].rekkefølgeIndeks shouldBe 3
    }

    @Test
    fun `Skal returnere dokumenter med hoveddokument først og vedleggene sortert rekkefølge hvis hoveddokument slettet`() {
        val hoveddokument = nyttDokument(tittel = "HOVEDDOK", slettet = true)
        val vedlegg1 = nyttDokument(rekkefølgeIndeks = 1, tittel = "VEDLEGG1")
        val vedlegg2 = nyttDokument(rekkefølgeIndeks = 2, tittel = "VEDLEGG2")
        val vedlegg3 = nyttDokument(rekkefølgeIndeks = 3, tittel = "VEDLEGG3")

        val dokumenter = listOf(hoveddokument, vedlegg2, vedlegg1, vedlegg3)

        val sortertDokumenter = dokumenter.sortertEtterRekkefølge

        sortertDokumenter[0].tittel shouldBe "VEDLEGG1"
        sortertDokumenter[1].tittel shouldBe "VEDLEGG2"
        sortertDokumenter[2].tittel shouldBe "VEDLEGG3"
        sortertDokumenter[3].tittel shouldBe "HOVEDDOK"

        sortertDokumenter[0].rekkefølgeIndeks shouldBe 0
        sortertDokumenter[1].rekkefølgeIndeks shouldBe 1
        sortertDokumenter[2].rekkefølgeIndeks shouldBe 2
        sortertDokumenter[3].rekkefølgeIndeks shouldBe 3
    }
}
