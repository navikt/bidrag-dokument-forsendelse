package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.dokument.forsendelse.model.PersonIdent
import no.nav.bidrag.dokument.forsendelse.model.toStringByReflection
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import no.nav.bidrag.transport.dokument.isForsendelse
import no.nav.bidrag.transport.dokument.isNumeric
import java.time.LocalDateTime

enum class JournalTema {
    BID,
    FAR,
}
typealias JournalpostId = String

val JournalpostId.utenPrefiks get() = this.replace("\\D".toRegex(), "")
val JournalpostId.harArkivPrefiks get() = this.contains("-")
val JournalpostId.erForsendelse get() = this.startsWith("BIF") || this.isNumeric && isForsendelse(this)
val JournalpostId.arkivsystem
    get(): DokumentArkivSystemDto? =
        if (!harArkivPrefiks) {
            null
        } else if (this.startsWith(
                "JOARK",
            )
        ) {
            DokumentArkivSystemDto.JOARK
        } else if (this.startsWith("BIF")) {
            DokumentArkivSystemDto.BIDRAG
        } else {
            DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
        }

@Schema(description = "Metadata til en respons etter dokumenter i forsendelse ble opprettet")
data class DokumentRespons(
    val dokumentreferanse: String,
    @Schema(description = "Originale dokumentreferanse hvis er kopi av en ekstern dokument (feks fra JOARK)") val originalDokumentreferanse:
        String? = null,
    @Schema(
        description = "Originale journalpostid hvis er kopi av en ekstern dokument (feks fra JOARK)",
    ) val originalJournalpostId: String? =
        null,
    val forsendelseId: String? = null,
    val tittel: String,
    val dokumentDato: LocalDateTime,
    val journalpostId: String? = null,
    val dokumentmalId: String? = null,
    val redigeringMetadata: String? = null,
    val erSkjema: Boolean = false,
    val status: DokumentStatusTo? = null,
    @Schema(enumAsRef = true) val arkivsystem: DokumentArkivSystemDto? = null,
)

@Schema(
    description =
        "Metadata for dokument som skal knyttes til forsendelsen. " +
            "Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen",
)
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class DokumentForespørsel(
    @Schema(description = "Dokumentets tittel") open val tittel: String? = null,
    @Schema(description = "DokumentmalId sier noe om dokumentets innhold og oppbygning. (Også kjent som brevkode)") open val dokumentmalId:
        String? = null,
    @Schema(description = "Dato dokument ble opprettet") open val dokumentDato: LocalDateTime? = null,
    @Schema(
        description =
            "Referansen til dokumentet hvis det er allerede er lagret i arkivsystem. " +
                "Hvis dette ikke settes opprettes det en ny dokumentreferanse som kan brukes ved opprettelse av dokument",
    ) open val dokumentreferanse: String? = null,
    @Schema(description = "JournalpostId til dokumentet hvis det er allerede er lagret i arkivsystem") open val journalpostId:
        JournalpostId? = null,
    @Schema(description = "Arkivsystem hvor dokument er lagret", enumAsRef = true) open val arkivsystem: DokumentArkivSystemDto? = null,
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
    @Schema(description = "Adresse til mottaker hvis dokumentet sendes som brev") val adresse: MottakerAdresseTo? = null,
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

@Schema(enumAsRef = true)
enum class MottakerIdentTypeTo {
    FNR,
    SAMHANDLER,
}

@Schema(
    description =
        "Dette skal være UNDER_PRODUKSJON for redigerbare dokumenter som ikke er ferdigprodusert. " +
            "Ellers settes det til FERDIGSTILT",
    enumAsRef = true,
)
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

@Schema(description = "Type på forsendelse. Kan være NOTAT eller UTGÅENDE", enumAsRef = true)
enum class ForsendelseTypeTo {
    UTGÅENDE,
    NOTAT,
}

@Schema(description = "Status på forsendelsen", enumAsRef = true)
enum class ForsendelseStatusTo {
    UNDER_OPPRETTELSE,
    UNDER_PRODUKSJON,
    FERDIGSTILT,
    SLETTET,
    DISTRIBUERT,
    DISTRIBUERT_LOKALT,
}
