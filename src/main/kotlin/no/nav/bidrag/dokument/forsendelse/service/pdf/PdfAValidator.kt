package no.nav.bidrag.dokument.forsendelse.service.pdf

import jakarta.persistence.spi.TransformerException
import mu.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.xmpbox.XMPMetadata
import org.apache.xmpbox.schema.DublinCoreSchema
import org.apache.xmpbox.schema.PDFAIdentificationSchema
import org.apache.xmpbox.type.BadFieldValueException
import org.apache.xmpbox.xml.XmpSerializer
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.gf.model.GFModelParser
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.PDFAValidator
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.ValidationResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

private val log = KotlinLogging.logger {}

private val validPdfas = listOf(PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B, PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFA_2_B, PDFAFlavour.PDFA_2_U)
fun ValidationResult.readableMessage() = testAssertions.joinToString(",") { it.message }
fun erGyldigPDFA(pdfBytes: ByteArray, dokumentreferanse: String): Boolean {
    try {
        VeraGreenfieldFoundryProvider.initialise()
        Foundries.defaultInstance().createParser(ByteArrayInputStream(pdfBytes)).use {
            // Hvis PDF/A'en ikke er på et av de lovlige foratene hopp over valideringen
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
    } catch (e: Exception) {
        log.warn(e) { "Det skjedde en feil ved validering av PDF" }
        return false
    }
}

fun validerPDFA(pdfBytes: ByteArray): String? {
    VeraGreenfieldFoundryProvider.initialise()
    Foundries.defaultInstance().createParser(ByteArrayInputStream(pdfBytes)).use {
        // Hvis PDF/A'en ikke er på et av de lovlige foratene hopp over valideringen
        if (!validPdfas.contains(it.flavour)) {
            return null
        }
        val validator = Foundries.defaultInstance().createValidator(it.flavour, false)
        val result = validator.validate(it)
        val pageTree = (it as GFModelParser).pdDocument.catalog.pageTree
        val pagesCount = pageTree.pageCount
        val invalidObjects = (0..<pagesCount).flatMap { i ->
            pageTree.getPage(i).resources.xObjectNames.filter { name ->
                pageTree.getPage(i).resources.getXObject(name) == null
            }.map { name -> name.value }
        }
        if (!result.isCompliant) {
            return "Dokumentet har ugyldig Xobject $invalidObjects Dokument er ikke en gyldig PDF/A: ${result.readableMessage()}"
        }
        return null
    }
}

fun convertToPDFA(pdfByte: ByteArray, title: String): ByteArray {
    Loader.loadPDF(pdfByte).use { pdfDocument ->
        // add XMP metadata
        val xmp: XMPMetadata = XMPMetadata.createXMPMetadata()
        try {
            val dc: DublinCoreSchema = xmp.createAndAddDublinCoreSchema()
            dc.title = title
            val id: PDFAIdentificationSchema = xmp.createAndAddPDFAIdentificationSchema()
            id.part = 1
            id.conformance = "B"
            val serializer = XmpSerializer()
            val baos = ByteArrayOutputStream()
            serializer.serialize(xmp, baos, true)
            val metadata = PDMetadata(pdfDocument)
            metadata.importXMPMetadata(baos.toByteArray())
            pdfDocument.documentCatalog.metadata = metadata

            val colorProfile: InputStream? = PDFAValidator::class.java.getResourceAsStream(
                "/files/colorprofiles/sRGB2014.icc"
            )

            val intent = PDOutputIntent(pdfDocument, colorProfile)

            intent.info = "sRGB IEC61966-2.1"
            intent.outputCondition = "sRGB IEC61966-2.1"
            intent.outputConditionIdentifier = "sRGB IEC61966-2.1"
            intent.registryName = "http://www.color.org"
            pdfDocument.documentCatalog.addOutputIntent(intent)

            val outputStream = ByteArrayOutputStream()
            pdfDocument.save(outputStream)
            return outputStream.toByteArray()
        } catch (e: BadFieldValueException) {
            // won't happen here, as the provided value is valid
            throw IllegalArgumentException(e)
        } catch (e: TransformerException) {
            throw IllegalArgumentException(e)
        }
    }
}
