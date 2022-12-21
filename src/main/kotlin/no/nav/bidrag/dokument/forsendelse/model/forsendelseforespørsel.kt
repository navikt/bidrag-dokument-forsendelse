package no.nav.bidrag.dokument.forsendelse.model

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Metadata for oppdatering av forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterForsendelseForespørsel(
    @Schema(description = "Ident til brukeren som journalposten gjelder") val gjelderIdent: String? = null,
    val mottaker: MottakerDto? = null,
    @Schema(description = "Liste over dokumentene på journalposten der metadata skal oppdateres")
    val dokumenter: List<OppdaterDokumentForespørsel> = emptyList(),
    @Schema(description = "Bidragsak som forsendelse skal tilknyttes") val saksnummer: String? = null,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String? = null
)

@Schema(description = "Metadata til en respons etter journalpost ble opprettet")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterForsendelseResponse(
    @Schema(description = "ForsendelseId på forsendelse som ble opprettet") val forsendelseId: String? = null,
    @Schema(description = "Liste med dokumenter som er knyttet til journalposten") val dokumenter: List<OppdaterDokumentRespons> = emptyList(),
)

@Schema(description = "Metadata til en respons etter dokumenter i forsendelse ble opprettet")
data class OppdaterDokumentRespons(
    val dokumentreferanse: String,
    val tittel: String,
    val journalpostId: String? = null
)

@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterDokumentForespørsel(
    @Schema(description = "Om tilknytning til forsendelse skal fjernes") val fjernTilknytning: Boolean = false):
    DokumentForespørsel() {
        override fun toString(): String {
            return super.toString() + " fjernTilknytning=${fjernTilknytning}"
        }
}

@Schema(description = "Metadata for opprettelse av forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettForsendelseForespørsel(
    @Schema(description = "Ident til brukeren som journalposten gjelder") val gjelderIdent: String,
    val mottaker: MottakerDto? = null,
    @Schema(description = """
    Dokumenter som skal knyttes til journalpost. 
    En journalpost må minst ha et dokument. 
    Det første dokument i meldingen blir tilknyttet som hoveddokument på journalposten.""", required = true)
    val dokumenter: List<OpprettDokumentForespørsel> = emptyList(),
    @Schema(description = "Bidragsak som forsendelse skal tilknyttes") val saksnummer: String,
    @Schema(description = "Forsendelsetype, dette kan enten være Utgående eller Notat", required = true) val forsendelseTypeTo: ForsendelseTypeTo = ForsendelseTypeTo.UTGÅENDE,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String,
    @Schema(description = "Ident til saksbehandler som oppretter journalpost. Dette vil prioriteres over ident som tilhører tokenet til kallet.") val saksbehandlerIdent: String? = null,
)
@Schema(description = "Metadata til en respons etter forsendelse ble opprettet")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettForsendelseSvar(
    @Schema(description = "ForsendelseId på forsendelse som ble opprettet") val forsendelseId: Long? = null,
    @Schema(description = "Liste med dokumenter som er knyttet til journalposten") val dokumenter: List<DokumentRespons> = emptyList(),
)
@Schema(description = "Metadata til en respons etter dokumenter i forsendelse ble opprettet")
data class DokumentRespons(
    val dokumentreferanse: String,
    val tittel: String,
    val journalpostId: String? = null
)
@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettDokumentForespørsel(
    @Schema(description = "Dokumentets tittel") override val tittel: String = "",
): DokumentForespørsel()
@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class DokumentForespørsel(
    @Schema(description = "Dokumentets tittel") open val tittel: String? = "",
    @Schema(description = "Om dokumentet skal være tilknyttet som hoveddokument eller vedlegg til forsendelsen") val tilknyttetSom: DokumentTilknyttetSomTo? = null,
    @Schema(description = "DokumentmalId sier noe om dokumentets innhold og oppbygning. (Også kjent som brevkode)") val dokumentmalId: String? = null,
    @Schema(description = "Referansen til dokumentet hvis det er allerede er lagret i arkivsystem. Hvis dette ikke settes opprettes det en ny dokumentreferanse som kan brukes ved opprettelse av dokument") val dokumentreferanse: String? = null,
    @Schema(description = "JournalpostId til dokumentet hvis det er allerede er lagret i arkivsystem") val journalpostId: String? = null,
    @Schema(description = "Selve PDF dokumentet formatert som Base64. Dette skal bare settes hvis dokumentet er redigert.") val fysiskDokument: ByteArray? = null,
    @Schema(description = "Dette skal være UNDER_PRODUKSJON for redigerbare dokumenter som ikke er ferdigprodusert. Ellers settes det til FERDIGSTILT") val status: DokumentStatusTo = DokumentStatusTo.FERDIGSTILT,
    @Schema(description = "Arkivsystem hvor dokument er lagret") val arkivsystem: DokumentArkivSystemTo = DokumentArkivSystemTo.BREVSERVER,
) {
    override fun toString(): String {
        return "(tittel=${tittel}, dokumentmalId=${dokumentmalId}, dokumentreferanse=${dokumentreferanse},journalpostId=${journalpostId} " +
                "fysiskDokument(lengde)=${fysiskDokument?.size ?: 0} status=${status}, arkivsystem=${arkivsystem}"
    }
}

data class MottakerDto(
    val ident: String? = null,
    val navn: String? = null,
    val identType: MottakerIdentTypeTo? = null,
    @Schema(description = "Adresse til mottaker hvis dokumentet sendes som brev") val adresse: MottakerAdresseTo? = null
)

data class MottakerAdresseTo(
    val adresselinje1: String,
    val adresselinje2: String,
    val adresselinje3: String,
    val bruksenhetsnummer: String,
    @Schema(description = "Lankode må være i format") val landkode: String,
    val postnummer: String,
    val poststed: String,
)

enum class MottakerIdentTypeTo {
    ORGANISASJON,
    FNR,
    SAMHANDLER
}

enum class DokumentStatusTo {
    UNDER_PRODUKSJON,
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
