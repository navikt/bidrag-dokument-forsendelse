package no.nav.bidrag.dokument.forsendelse.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.dto.ÅpneDokumentMetadata
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.tjeneste.FysiskDokumentTjeneste
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@ForsendelseApiKontroller
class DokumentKontroller(val fysiskDokumentTjeneste: FysiskDokumentTjeneste) {

    @GetMapping("/dokument/{forsendelseIdMedPrefix}/{dokumentreferanse}")
    @Operation(
        summary = "Hent fysisk dokument som byte",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentDokument(@PathVariable forsendelseIdMedPrefix: String, @PathVariable dokumentreferanse: String): ByteArray {
        return fysiskDokumentTjeneste.hentDokument(forsendelseIdMedPrefix.numerisk, dokumentreferanse)
    }

    @RequestMapping(*["/dokument/{forsendelseIdMedPrefix}/{dokumentreferanse}" ,"/dokument/{forsendelseIdMedPrefix}"], method = [RequestMethod.OPTIONS])
    @Operation(
        summary = "Hent metadata om dokument",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun hentDokumentMetadata(@PathVariable forsendelseIdMedPrefix: String, @PathVariable(required = false) dokumentreferanse: String?): List<ÅpneDokumentMetadata> {
        return fysiskDokumentTjeneste.hentDokumentMetadata(forsendelseIdMedPrefix.numerisk, dokumentreferanse)
    }
}