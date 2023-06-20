package no.nav.bidrag.dokument.forsendelse.service.pdf

import mu.KotlinLogging
import org.apache.pdfbox.pdmodel.PDDocument

private val log = KotlinLogging.logger {}

class PDFDokumentDetails {
    fun getNumberOfPages(dokumentFil: ByteArray): Int {
        try {
            PDDocument.load(dokumentFil).use { document ->
                val numberOfPages = document.numberOfPages
                document.close()
                return numberOfPages
            }
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved prossesering av PDF dokument", e)
            return 0
        }
    }
}
