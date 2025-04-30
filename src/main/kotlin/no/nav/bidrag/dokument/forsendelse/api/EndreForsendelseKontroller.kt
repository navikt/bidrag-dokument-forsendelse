package no.nav.bidrag.dokument.forsendelse.api

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.api.utvidelser.tilOppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.service.OppdaterForsendelseService
import no.nav.bidrag.transport.dokument.EndreJournalpostCommand
import no.nav.bidrag.transport.dokument.forsendelse.DokumentRespons
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterDokumentForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterForsendelseForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OppdaterForsendelseResponse
import no.nav.bidrag.transport.dokument.forsendelse.OpprettDokumentForespørsel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@ForsendelseApiKontroller
@Timed
class EndreForsendelseKontroller(
    val oppdaterForsendelseService: OppdaterForsendelseService,
) {
    @PatchMapping("/{forsendelseIdMedPrefix}")
    @Operation(
        summary = "Endre forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse endret"),
            ApiResponse(responseCode = "404", description = "Fant ingen forsendelse for forsendelseId"),
        ],
    )
    fun oppdaterForsendelse(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @RequestBody request: OppdaterForsendelseForespørsel,
    ): OppdaterForsendelseResponse {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return oppdaterForsendelseService.oppdaterForsendelse(forsendelseId, request)
    }

    @PatchMapping("/journal/{forsendelseIdMedPrefix}")
    @Operation(
        summary = "Endre forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse endret"),
            ApiResponse(responseCode = "404", description = "Fant ingen forsendelse for forsendelseId"),
        ],
    )
    fun oppdaterForsendelseLegacy(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @RequestBody request: EndreJournalpostCommand,
    ) {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        oppdaterForsendelseService.oppdaterForsendelse(forsendelseId, request.tilOppdaterForsendelseForespørsel())
    }

    @PatchMapping("/{forsendelseIdMedPrefix}/dokument/{dokumentreferanse}")
    @Operation(
        summary = "Oppdater dokument i en forsendelsee",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ],
    )
    fun oppdaterDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
        @RequestBody forespørsel: OppdaterDokumentForespørsel,
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return oppdaterForsendelseService.oppdaterDokument(forsendelseId, dokumentreferanse, forespørsel)
    }

    @PostMapping("/{forsendelseIdMedPrefix}/dokument")
    @Operation(
        summary = "Knytt eller opprett ny dokument til forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ],
    )
    fun knyttTilDokument(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @RequestBody forespørsel: OpprettDokumentForespørsel,
    ): DokumentRespons {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        return oppdaterForsendelseService.knyttDokumentTilForsendelse(forsendelseId, forespørsel)
    }

    @DeleteMapping("/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Slett dokument fra forsendelse",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Forsendelse hentet"),
            ApiResponse(responseCode = "202", description = "Fant ingen forsendelse for id"),
        ],
    )
    fun fjernDokumentFraForsendelse(
        @PathVariable forsendelseIdMedPrefix: ForsendelseId,
        @PathVariable dokumentreferanse: String,
    ): ResponseEntity<OppdaterForsendelseResponse> {
        val forsendelseId = forsendelseIdMedPrefix.numerisk
        val respons =
            oppdaterForsendelseService.fjernDokumentFraForsendelse(forsendelseId, dokumentreferanse)
                ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(respons)
    }
}
