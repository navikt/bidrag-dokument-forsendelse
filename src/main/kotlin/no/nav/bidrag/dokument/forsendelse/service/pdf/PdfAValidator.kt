package no.nav.bidrag.dokument.forsendelse.service.pdf

import mu.KotlinLogging
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.ValidationResult
import java.io.ByteArrayInputStream

private val log = KotlinLogging.logger {}

private val validPdfas = listOf(PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B, PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFA_2_B, PDFAFlavour.PDFA_2_U)
fun ValidationResult.readableMessage() = testAssertions.joinToString(",") { it.message }
fun erGyldigPDFA(pdfBytes: ByteArray, dokumentreferanse: String): Boolean {
    VeraGreenfieldFoundryProvider.initialise()
    Foundries.defaultInstance().createParser(ByteArrayInputStream(pdfBytes)).use {
        //Hvis PDF/A'en ikke er på et av de lovlige foratene hopp over valideringen
        if (!validPdfas.contains(it.flavour)) {
            log.warn { "Dokument $dokumentreferanse er ikke en PDF/A dokument. Den er av typen ${it.flavour}" }
            return false
        }
        val validator = Foundries.defaultInstance().createValidator(it.flavour, false)
        val result = validator.validate(it)

        if (!result.isCompliant) {
            log.warn { "Dokument $dokumentreferanse er ikke en gyldig PDF/A: ${result.readableMessage()}" }
        } else {
            log.info { "Dokument $dokumentreferanse er gyldig PDF/A med type ${result.pdfaFlavour}" }
        }
        return result.isCompliant
    }
}

fun validerPDFA(pdfBytes: ByteArray): String? {
    VeraGreenfieldFoundryProvider.initialise()
    Foundries.defaultInstance().createParser(ByteArrayInputStream(pdfBytes)).use {
        //Hvis PDF/A'en ikke er på et av de lovlige foratene hopp over valideringen
        if (!validPdfas.contains(it.flavour)) {
            return null
        }
        val validator = Foundries.defaultInstance().createValidator(it.flavour, false)
        val result = validator.validate(it)

        if (!result.isCompliant) {
            return "Dokument er ikke en gyldig PDF/A: ${result.readableMessage()}"
        }
        return null
    }
}