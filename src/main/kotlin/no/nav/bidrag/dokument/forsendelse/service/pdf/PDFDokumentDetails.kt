package no.nav.bidrag.dokument.forsendelse.service.pdf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader

private val log = KotlinLogging.logger {}

class PDFDokumentDetails {
    fun getNumberOfPages(dokumentFil: ByteArray): Int {
        try {
            Loader.loadPDF(dokumentFil).use { document ->
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
