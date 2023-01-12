package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto

@Schema(description = "Metadata for opprettelse av forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettForsendelseForespørsel(
    @Schema(description = "Ident til brukeren som journalposten gjelder") val gjelderIdent: String,
    val mottaker: MottakerTo? = null,
    @Schema(description = """
    Dokumenter som skal knyttes til journalpost. 
    En journalpost må minst ha et dokument. 
    Det første dokument i meldingen blir tilknyttet som hoveddokument på journalposten.""", required = true)
    val dokumenter: List<OpprettDokumentForespørsel> = emptyList(),
    @Schema(description = "Bidragsak som forsendelse skal tilknyttes") val saksnummer: String,
    @Schema(description = "Forsendelsetype, dette kan enten være Utgående eller Notat", required = true) val forsendelseType: ForsendelseTypeTo? = null,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String,
    @Schema(description = "Språk forsendelsen skal være på") val språk: String? = null,
    @Schema(description = "Ident til saksbehandler som oppretter journalpost. Dette vil prioriteres over ident som tilhører tokenet til kallet.") val saksbehandlerIdent: String? = null,
)
@Schema(description = "Metadata til en respons etter forsendelse ble opprettet")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettForsendelseRespons(
    @Schema(description = "ForsendelseId på forsendelse som ble opprettet") val forsendelseId: Long? = null,
    @Schema(description = "Liste med dokumenter som er knyttet til journalposten") val dokumenter: List<DokumentRespons> = emptyList(),
)

@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettDokumentForespørsel(
    @Schema(description = "Dokumentets tittel") override val tittel: String = "",
    @Schema(description = "Arkivsystem hvor dokument er lagret") override val arkivsystem: DokumentArkivSystemDto? = null,
    @Schema(description = "Referansen til dokumentet hvis det er allerede er lagret i arkivsystem. Hvis dette ikke settes opprettes det en ny dokumentreferanse som kan brukes ved opprettelse av dokument") override val dokumentreferanse: String? = null,
    @Schema(description = "JournalpostId til dokumentet hvis det er allerede er lagret i arkivsystem") override val journalpostId: JournalpostId? = null,
    @Schema(description = "DokumentmalId sier noe om dokumentets innhold og oppbygning. (Også kjent som brevkode)") override val dokumentmalId: String? = null,
    @Schema(description = "Om dokumentet med oppgitt dokumentmalId skal bestilles der dokumentreferanse/journalpostid ikke er oppgitt.") val bestillDokument: Boolean = true,
    @Schema(description = "Dette skal være UNDER_PRODUKSJON for redigerbare dokumenter som ikke er ferdigprodusert. Ellers settes det til FERDIGSTILT") override val status: DokumentStatusTo = DokumentStatusTo.FERDIGSTILT,
    ): DokumentForespørsel()