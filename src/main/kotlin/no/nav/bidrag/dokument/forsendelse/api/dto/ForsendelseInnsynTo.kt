package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Metadata om forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ForsendelseResponsTo(
    @Schema(description = "Ident til brukeren som journalposten gjelder") val gjelderIdent: String? = null,
    val mottaker: MottakerTo? = null,
    @Schema(description = "Liste over dokumentene på journalposten der metadata skal oppdateres")
    val dokumenter: List<DokumentRespons> = emptyList(),
    @Schema(description = "Bidragsak som forsendelsen er knyttet til") val saksnummer: String? = null,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String? = null,
    @Schema(description = "Ident på saksbehandler eller applikasjon som opprettet forsendelsen") val opprettetAvIdent: String? = null,
    @Schema(description = "Navn på saksbehandler eller applikasjon som opprettet forsendelsen") val opprettetAvNavn: String? = null,
    @Schema(description = "Tittel på hoveddokumentet i forsendelsen") val tittel: String? = null,
    @Schema(description = "Journalpostid som forsendelsen ble arkivert på. Dette vil bli satt hvis status er FERDIGSTILT") val arkivJournalpostId: String? = null,
    @Schema(description = "Type på forsendelse. Kan være NOTAT eller UTGÅENDE") val forsendelseType: ForsendelseTypeTo? = null,
    @Schema(description = "Status på forsendelsen") val status: ForsendelseStatusTo? = null,
    @Schema(description = "Dato forsendelsen ble opprettet") val opprettetDato: LocalDate? = null,
    @Schema(description = "Dato på hoveddokumentet i forsendelsen") val dokumentDato: LocalDate? = null,
    @Schema(description = "Dato forsendelsen ble distribuert") val distribuertDato: LocalDate? = null
)