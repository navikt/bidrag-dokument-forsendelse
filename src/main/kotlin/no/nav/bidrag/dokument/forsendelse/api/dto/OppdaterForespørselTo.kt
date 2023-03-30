package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Metadata for oppdatering av forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterForsendelseForespørsel(
    @Schema(description = "Liste over dokumentene på journalposten der metadata skal oppdateres")
    val dokumenter: List<OppdaterDokumentForespørsel> = emptyList(),
    @Schema(description = "Dato hoveddokument i forsendelsen ble opprettet") val dokumentDato: LocalDateTime? = null
)

@Schema(description = "Metadata til en respons etter journalpost ble oppdatert")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterForsendelseResponse(
    @Schema(description = "ForsendelseId på forsendelse som ble opprettet") val forsendelseId: String? = null,
    @Schema(description = "Liste med dokumenter som er knyttet til journalposten") val dokumenter: List<DokumentRespons> = emptyList()
)

@Schema(description = "Metadata for dokument som skal knyttes til forsendelsen. Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OppdaterDokumentForespørsel(
    @Schema(description = "JournalpostId til dokumentet hvis det er allerede er lagret i arkivsystem") override val journalpostId: JournalpostId? = null,
    override val dokumentmalId: String? = null,
    override val dokumentreferanse: String? = null,
    override val tittel: String? = null,
    val fjernTilknytning: Boolean? = false
) : DokumentForespørsel()
