package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.apache.commons.lang3.Range

@Schema(description = "Metadata for oppdatering av forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterForsendelseForespørsel(
    @Schema(description = "Ident til brukeren som journalposten gjelder") val gjelderIdent: String? = null,
    val mottaker: MottakerTo? = null,
    @Schema(description = "Liste over dokumentene på journalposten der metadata skal oppdateres")
    val dokumenter: List<OppdaterDokumentForespørsel> = emptyList(),
    @Schema(description = "Bidragsak som forsendelse skal tilknyttes") val saksnummer: String? = null,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String? = null,
    @Schema(description = "Språk forsendelsen skal være på") val språk: String? = null
)

@Schema(description = "Metadata til en respons etter journalpost ble oppdatert")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterForsendelseResponse(
    @Schema(description = "ForsendelseId på forsendelse som ble opprettet") val forsendelseId: String? = null,
    @Schema(description = "Liste med dokumenter som er knyttet til journalposten") val dokumenter: List<DokumentRespons> = emptyList(),
)

@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterDokumentForespørsel(
    override val dokumentmalId: String? = null,
    override val dokumentreferanse: String? = null,
    override val tittel: String? = null,
    @Schema(description = "Om tilknytning til forsendelse skal fjernes") val fjernTilknytning: Boolean = false
): DokumentForespørsel() {
        override fun toString(): String {
            return super.toString() + " fjernTilknytning=${fjernTilknytning}"
        }
}
