package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.model.PersonIdent
import no.nav.bidrag.dokument.forsendelse.model.toStringByReflection
import org.apache.commons.lang3.Range
import java.time.LocalDateTime

val BID_JP_RANGE: Range<Long> = Range.between(18900000L, 40000000L)

enum class JournalTema {
    BID,
    FAR
}
typealias JournalpostId = String

val JournalpostId.utenPrefiks get() = this.replace("\\D".toRegex(), "")
val JournalpostId.harArkivPrefiks get() = this.contains("-")
val JournalpostId.arkivsystem
    get(): DokumentArkivSystemDto? = if (!harArkivPrefiks) null else if (this.startsWith(
            "JOARK"
        )
    ) DokumentArkivSystemDto.JOARK else DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER

@Schema(description = "Metadata til en respons etter dokumenter i forsendelse ble opprettet")
data class DokumentRespons(
    val dokumentreferanse: String,
    val tittel: String,
    val dokumentDato: LocalDateTime,
    val journalpostId: String? = null,
    val dokumentmalId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val status: DokumentStatusTo? = null,
    val arkivsystem: DokumentArkivSystemDto? = null
)

@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class DokumentForespørsel(
    @Schema(description = "Dokumentets tittel") open val tittel: String? = null,
    @Schema(description = "DokumentmalId sier noe om dokumentets innhold og oppbygning. (Også kjent som brevkode)") open val dokumentmalId: String? = null,
    @Schema(description = "Dato dokument ble opprettet") open val dokumentDato: LocalDateTime? = null,
    @Schema(description = "Referansen til dokumentet hvis det er allerede er lagret i arkivsystem. Hvis dette ikke settes opprettes det en ny dokumentreferanse som kan brukes ved opprettelse av dokument") open val dokumentreferanse: String? = null,
    @Schema(description = "JournalpostId til dokumentet hvis det er allerede er lagret i arkivsystem") open val journalpostId: JournalpostId? = null,
    @Schema(description = "Arkivsystem hvor dokument er lagret") open val arkivsystem: DokumentArkivSystemDto? = null,
    @Schema(description = "Dokument metadata") open val metadata: Map<String, String> = emptyMap(),

    ) {
    override fun toString(): String {
        return this.toStringByReflection(mask = listOf("fysiskDokument"))
    }

}

data class MottakerTo(
    val ident: PersonIdent? = null,
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
    @Schema(description = "Lankode må være i ISO 3166-1 alpha-2 format") val landkode: String? = null,
    @Schema(description = "Lankode må være i ISO 3166-1 alpha-3 format") val landkode3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
)

enum class MottakerIdentTypeTo {
    FNR,
    SAMHANDLER
}

enum class DokumentStatusTo {
    IKKE_BESTILT,
    BESTILLING_FEILET,
    AVBRUTT,
    UNDER_PRODUKSJON,
    UNDER_REDIGERING,
    FERDIGSTILT,
    MÅ_KONTROLLERES,
    KONTROLLERT,
}

enum class ForsendelseTypeTo {
    UTGÅENDE,
    NOTAT
}

enum class ForsendelseStatusTo {
    UNDER_PRODUKSJON,
    FERDIGSTILT,
    SLETTET,
    DISTRIBUERT,
    DISTRIBUERT_LOKALT
}