package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.apache.commons.lang3.Range

val BID_JP_RANGE: Range<Long> = Range.between(18900000L, 40000000L)

typealias JournalpostId = String

val JournalpostId.utenPrefiks get() = this.replace("\\D".toRegex(), "")
val JournalpostId.harArkivPrefiks get() = this.contains("-")
val JournalpostId.arkivsystem get(): DokumentArkivSystemTo? = if (!harArkivPrefiks) null else if(this.startsWith("JOARK")) DokumentArkivSystemTo.JOARK else DokumentArkivSystemTo.BREVSERVER

@Schema(description = "Metadata til en respons etter dokumenter i forsendelse ble opprettet")
data class DokumentRespons(
    val dokumentreferanse: String,
    val tittel: String,
    val journalpostId: String? = null,
    val dokumentmalId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val status: DokumentStatusTo? = null,
    val arkivsystem: DokumentArkivSystemTo? = null
)

@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class DokumentForespørsel(
    @Schema(description = "Dokumentets tittel") open val tittel: String? = "",
    @Schema(description = "Om dokumentet skal være tilknyttet som hoveddokument eller vedlegg til forsendelsen") val tilknyttetSom: DokumentTilknyttetSomTo? = null,
    @Schema(description = "DokumentmalId sier noe om dokumentets innhold og oppbygning. (Også kjent som brevkode)") val dokumentmalId: String? = null,
    @Schema(description = "Referansen til dokumentet hvis det er allerede er lagret i arkivsystem. Hvis dette ikke settes opprettes det en ny dokumentreferanse som kan brukes ved opprettelse av dokument") val dokumentreferanse: String? = null,
    @Schema(description = "JournalpostId til dokumentet hvis det er allerede er lagret i arkivsystem") val journalpostId: JournalpostId? = null,
    @Schema(description = "Selve PDF dokumentet formatert som Base64. Dette skal bare settes hvis dokumentet er redigert.") val fysiskDokument: ByteArray? = null,
    @Schema(description = "Dette skal være UNDER_PRODUKSJON for redigerbare dokumenter som ikke er ferdigprodusert. Ellers settes det til FERDIGSTILT") val status: DokumentStatusTo = DokumentStatusTo.FERDIGSTILT,
    @Schema(description = "Arkivsystem hvor dokument er lagret") val arkivsystem: DokumentArkivSystemTo? = null,
    @Schema(description = "Dokument metadata") val metadata: Map<String, String> = emptyMap(),

    ) {
    override fun toString(): String {
        return "tittel=${tittel}, dokumentmalId=${dokumentmalId}, dokumentreferanse=${dokumentreferanse},journalpostId=${journalpostId} " +
                "fysiskDokument(lengde)=${fysiskDokument?.size ?: 0} status=${status}, arkivsystem=${arkivsystem}"
    }
}

data class MottakerTo(
    val ident: String? = null,
    val språk: String? = null,
    val navn: String? = null,
    val identType: MottakerIdentTypeTo? = null,
    @Schema(description = "Adresse til mottaker hvis dokumentet sendes som brev") val adresse: MottakerAdresseTo? = null
)

data class MottakerAdresseTo(
    val adresselinje1: String,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val bruksenhetsnummer: String? = null,
    @Schema(description = "Lankode må være i format") val landkode: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
)

enum class MottakerIdentTypeTo {
    ORGANISASJON,
    FNR,
    SAMHANDLER
}

enum class DokumentStatusTo {
    IKKE_BESTILT,
    BESTILT,
    AVBRUTT,
    UNDER_PRODUKSJON,
    UNDER_REDIGERING,
    FERDIGSTILT
}

enum class DokumentTilknyttetSomTo {
    HOVEDDOKUMENT,
    VEDLEGG
}

enum class DokumentArkivSystemTo {
    JOARK,
    BREVSERVER
}

enum class ForsendelseTypeTo {
    UTGÅENDE,
    NOTAT
}

enum class ForsendelseStatusTo {
    UNDER_PRODUKSJON,
    FERDIGSTILT,
    SLETTET,
    DISTRIBUERT
}