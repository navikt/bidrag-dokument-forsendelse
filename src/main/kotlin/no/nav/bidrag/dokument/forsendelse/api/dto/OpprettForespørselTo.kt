package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import java.time.LocalDateTime

@Schema(description = "Metadata for opprettelse av forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettForsendelseForespørsel(
    @Schema(description = "Ident til brukeren som journalposten gjelder") val gjelderIdent: String,
    val mottaker: MottakerTo? = null,
    @Schema(
        description = """
    Dokumenter som skal knyttes til journalpost. 
    En journalpost må minst ha et dokument. 
    Det første dokument i meldingen blir tilknyttet som hoveddokument på journalposten.""",
        required = true
    )
    val dokumenter: List<OpprettDokumentForespørsel> = emptyList(),
    @Schema(description = "Bidragsak som forsendelse skal tilknyttes") val saksnummer: String,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String,
    @Schema(description = "Identifikator til batch kjøring forsendelsen ble opprettet av") val batchId: String? = null,
    @Schema(description = "Tema forsendelsen skal opprettes med") val tema: JournalTema? = null,
    @Schema(description = "Språk forsendelsen skal være på") val språk: String? = null,
    @Schema(description = "Ident til saksbehandler som oppretter journalpost. Dette vil prioriteres over ident som tilhører tokenet til kallet.") val saksbehandlerIdent: String? = null
)

@Schema(description = "Metadata til en respons etter forsendelse ble opprettet")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettForsendelseRespons(
    @Schema(description = "ForsendelseId på forsendelse som ble opprettet") val forsendelseId: Long? = null,
    @Schema(description = "Type på forsendelse. Kan være NOTAT eller UTGÅENDE") val forsendelseType: ForsendelseTypeTo? = null,
    @Schema(description = "Liste med dokumenter som er knyttet til journalposten") val dokumenter: List<DokumentRespons> = emptyList()
)

@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettDokumentForespørsel(
    @Schema(description = "Dokumentets tittel") override val tittel: String = "",
    @Schema(description = "Språket på inneholdet i dokumentet.") val språk: String? = null,
    @Schema(description = "Arkivsystem hvor dokument er lagret") override val arkivsystem: DokumentArkivSystemDto? = null,
    @Schema(description = "Dato dokument ble opprettet") override val dokumentDato: LocalDateTime? = null,
    @Schema(description = "Referansen til dokumentet hvis det er allerede er lagret i arkivsystem. Hvis dette ikke settes opprettes det en ny dokumentreferanse som kan brukes ved opprettelse av dokument") override val dokumentreferanse: String? = null,
    @Schema(description = "JournalpostId til dokumentet hvis det er allerede er lagret i arkivsystem") override val journalpostId: JournalpostId? = null,
    @Schema(description = "DokumentmalId sier noe om dokumentets innhold og oppbygning. (Også kjent som brevkode)") override val dokumentmalId: String? = null,
    @Schema(description = "Dette skal være UNDER_PRODUKSJON for redigerbare dokumenter som ikke er ferdigprodusert. Ellers settes det til FERDIGSTILT") val status: DokumentStatusTo = DokumentStatusTo.FERDIGSTILT,
    @Schema(description = "Om dokumentet med dokumentmalId skal bestilles. Hvis dette er satt til false så antas det at kallende system bestiller dokumentet selv.") val bestillDokument: Boolean = true
) : DokumentForespørsel()
