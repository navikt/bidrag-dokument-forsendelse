package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "Metadata om forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ForsendelseResponsTo(
    val forsendelseId: Long,
    @Schema(description = "Ident til brukeren som journalposten gjelder") val gjelderIdent: String? = null,
    val mottaker: MottakerTo? = null,
    @Schema(description = "Liste over dokumentene på journalposten der metadata skal oppdateres")
    val dokumenter: List<DokumentRespons> = emptyList(),
    @Schema(description = "Bidragsak som forsendelsen er knyttet til") val saksnummer: String? = null,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String? = null,
    @Schema(description = "Tema på forsendelsen") val tema: String? = null,
    @Schema(description = "Detaljer om behandling forsendelse er knyttet til") val behandlingInfo: BehandlingInfoResponseDto? = null,
    @Schema(description = "Detaljer om varsel ettersendelse") val ettersendingsoppgave: EttersendingsoppgaveDto? = null,
    @Schema(description = "Ident på saksbehandler eller applikasjon som opprettet forsendelsen") val opprettetAvIdent: String? = null,
    @Schema(description = "Navn på saksbehandler eller applikasjon som opprettet forsendelsen") val opprettetAvNavn: String? = null,
    @Schema(description = "Tittel på hoveddokumentet i forsendelsen") val tittel: String? = null,
    @Schema(
        description = "Journalpostid som forsendelsen ble arkivert på. Dette vil bli satt hvis status er FERDIGSTILT",
    ) val arkivJournalpostId: String? = null,
    @Schema(
        description = "Type på forsendelse. Kan være NOTAT eller UTGÅENDE",
        enumAsRef = true,
    ) val forsendelseType: ForsendelseTypeTo? = null,
    val status: ForsendelseStatusTo? = null,
    @Schema(description = "Dato forsendelsen ble opprettet") val opprettetDato: LocalDate? = null,
    @Schema(description = "Dato på hoveddokumentet i forsendelsen") val dokumentDato: LocalDate? = null,
    @Schema(description = "Dato forsendelsen ble distribuert") val distribuertDato: LocalDate? = null,
) {
    fun hentHoveddokument() = if (dokumenter.isEmpty()) null else dokumenter[0]
}

data class EttersendingsoppgaveDto(
    val tittel: String? = null,
    val ettersendelseForJournalpostId: String? = null,
    val skjemaId: String? = null,
    val innsendingsfristDager: Int? = null,
    val vedleggsliste: List<EttersendingsoppgaveVedleggDto> = emptyList(),
)

data class EttersendingsoppgaveVedleggDto(
    val tittel: String,
    val skjemaId: String? = null,
    val id: Long,
)

@Schema(description = "Metadata om behandling")
data class BehandlingInfoResponseDto(
    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val soknadId: String? = null,
    val behandlingType: BehandlingType? = null,
    val erFattet: Boolean? = null,
    val barnIBehandling: List<String>? = null,
)

@Schema(description = "Metadata om forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ForsendelseIkkeDistribuertResponsTo(
    @Schema(description = "Forsendelseid med BIF- prefiks") val forsendelseId: String? = null,
    @Schema(description = "Bidragsak som forsendelsen er knyttet til") val saksnummer: String? = null,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String? = null,
    @Schema(description = "Tittel på hoveddokumentet i forsendelsen") val tittel: String? = null,
    @Schema(description = "Dato forsendelsen ble opprettet") val opprettetDato: LocalDateTime? = null,
)
