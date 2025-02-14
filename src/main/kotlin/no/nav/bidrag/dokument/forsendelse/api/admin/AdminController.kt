package no.nav.bidrag.dokument.forsendelse.api.admin

import io.micrometer.core.annotation.Timed
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.dokument.forsendelse.hendelse.DokumentHendelseLytter
import no.nav.bidrag.dokument.forsendelse.hendelse.ForsendelseSkedulering
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseId
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.security.token.support.core.api.Protected
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@Protected
@RequestMapping("/api/forsendelse/internal")
@Timed
class AdminController(
    private val forsendelseSkedulering: ForsendelseSkedulering,
    private val dokumentHendelseLytter: DokumentHendelseLytter,
    private val forsendelseTjeneste: ForsendelseTjeneste,
) {
    @PostMapping("/distribusjon/navno")
    @Operation(
        summary = "Resynk distribusjonkanal for forsendelser som er distribuert via nav.no",
        description = """
Resynk distribusjonkanal. Hvis forsendelse er distribuert via nav.no og mottaker ikke har åpnet dokumentet i løpet av 48 timer vil forsendelsen bli redistribuert via sentral print. 
Denne tjenesten trigger en resynk av alle forsendelser som er sendt via nav.no for å oppdatere til riktig distribusjonstatus. Dette kjøres også som en egen skedulert jobb.
        """,
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun distTilNavNoMenHarKanalSentralPrint(
        @RequestParam(
            required = false,
            defaultValue = "true",
        ) simulering: Boolean = true,
        @Parameter(name = "afterDate", example = "2023-11-01")
        @RequestParam(
            required = false,
        )
        afterDate: LocalDate?,
        @Parameter(name = "beforeDate", example = "2023-12-31")
        @RequestParam(
            required = false,
        )
        beforeDate: LocalDate?,
        @RequestParam(
            required = false,
        ) sjekketNavNoRedistribusjonTilSentralPrint: Boolean = false,
        @RequestParam(
            required = false,
        ) pageSize: Int = 500,
    ): List<Map<String, String?>> =
        forsendelseSkedulering
            .resynkDistribusjoninfoNavNo(
                simulering,
                afterDate?.atStartOfDay(),
                beforeDate?.atStartOfDay(),
                sjekketNavNoRedistribusjonTilSentralPrint,
                pageSize,
            ).map {
                it.mapToResponse()
            }

    @PostMapping("/distribusjon/navno/{forsendelseId}")
    @Operation(
        summary = "Resynk distribusjonkanal for forsendelse",
        description = """
Resynk distribusjonkanal. Hvis forsendelse er distribuert via nav.no og mottaker ikke har åpnet dokumentet i løpet av 48 timer vil forsendelsen bli redistribuert via sentral print. 
Denne tjenesten trigger en resynk av alle forsendelser som er sendt via nav.no for å oppdatere til riktig distribusjonstatus. Dette kjøres også som en egen skedulert jobb.
        """,
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun distTilNavNoMenHarKanalSentralPrintForForsendelse(
        @PathVariable forsendelseId: Long,
        @RequestParam(
            required = false,
            defaultValue = "true",
        ) simulering: Boolean = true,
    ): Map<String, String?>? =
        forsendelseSkedulering
            .resynkDistribusjoninfoNavNoForForsendelse(
                forsendelseId,
                simulering,
            )?.mapToResponse()

    @PostMapping("/synkForsendelseDistribusjonStatus")
    @Operation(
        summary = "Sjekk status på dokumentene i en enkel forsendelse og oppdater status hvis det er ute av synk",
        description = """
Sjekk status på dokumentene i en enkel forsendelse og oppdater status hvis det er ute av synk. Dette skal brukes hvis feks en dokument er ferdigstilt i midlertidlig brevlager men status i databasen er fortsatt "under redigering"
Denne tjenesten vil sjekke om dokumentet er ferdigstilt og oppdatere status hvis det er det. Bruk denne tjenesten istedenfor å oppdatere databasen direkte da ferdigstilt notat blir automatisk arkivert i Joark.
        """,
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(value = [ApiResponse(responseCode = "400", description = "Fant ikke forsendelse med oppgitt forsendelsid")])
    fun synkForsendelseDistribusjonStatusForAlle() {
        forsendelseSkedulering.oppdaterDistribusjonstatus()
    }

    @PostMapping("/synkForsendelseDistribusjonStatus/{forsendelseId}")
    @Operation(
        summary = "Sjekk status på dokumentene i en enkel forsendelse og oppdater status hvis det er ute av synk",
        description = """
Sjekk status på dokumentene i en enkel forsendelse og oppdater status hvis det er ute av synk. Dette skal brukes hvis feks en dokument er ferdigstilt i midlertidlig brevlager men status i databasen er fortsatt "under redigering"
Denne tjenesten vil sjekke om dokumentet er ferdigstilt og oppdatere status hvis det er det. Bruk denne tjenesten istedenfor å oppdatere databasen direkte da ferdigstilt notat blir automatisk arkivert i Joark.
        """,
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(value = [ApiResponse(responseCode = "400", description = "Fant ikke forsendelse med oppgitt forsendelsid")])
    fun synkForsendelseDistribusjonStatus(
        @PathVariable forsendelseId: ForsendelseId,
    ) {
        forsendelseTjeneste.medForsendelseId(forsendelseId.numerisk)?.let { forsendelse ->
            forsendelseSkedulering.oppdaterForsendelsestatusTilDistribuert(forsendelse)
        }
    }

    @PostMapping("/sjekkOgOppdaterStatus/{forsendelseId}")
    @Operation(
        summary = "Sjekk status på dokumentene i en enkel forsendelse og oppdater status hvis det er ute av synk",
        description = """
Sjekk status på dokumentene i en enkel forsendelse og oppdater status hvis det er ute av synk. Dette skal brukes hvis feks en dokument er ferdigstilt i midlertidlig brevlager men status i databasen er fortsatt "under redigering"
Denne tjenesten vil sjekke om dokumentet er ferdigstilt og oppdatere status hvis det er det. Bruk denne tjenesten istedenfor å oppdatere databasen direkte da ferdigstilt notat blir automatisk arkivert i Joark.
        """,
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(value = [ApiResponse(responseCode = "400", description = "Fant ikke forsendelse med oppgitt forsendelsid")])
    fun sjekkOgOppdaterStatus(
        @PathVariable forsendelseId: ForsendelseId,
        @RequestParam(
            required = false,
            defaultValue = "false",
        ) oppdaterStatus: Boolean = false,
    ): List<Map<String, String?>> =
        forsendelseTjeneste.medForsendelseId(forsendelseId.numerisk)?.let { forsendelse ->
            forsendelse.dokumenter
                .flatMap {
                    dokumentHendelseLytter.sjekkOmDokumentErFerdigstiltOgOppdaterStatus(it, oppdaterStatus)
                }.map { it.mapToResponse() }
        } ?: emptyList()

    @PostMapping("/sjekkOgOppdaterStatus")
    @Operation(
        summary = "Sjekk status på dokumentene i forsendelser og oppdater status hvis det er ute av synk",
        description = """
Sjekk status på dokumentene i forsendelse og oppdater status hvis det er ute av synk. Dette skal brukes hvis feks en dokument er ferdigstilt i midlertidlig brevlager men status i databasen er fortsatt "under redigering"
Denne tjenesten vil sjekke om dokumentet er ferdigstilt og oppdatere status hvis det er det. Bruk denne tjenesten istedenfor å oppdatere databasen direkte da ferdigstilt notat blir automatisk arkivert i Joark.
        """,
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun sjekkOgOppdaterStatus(
        @RequestParam(
            required = true,
            defaultValue = "100",
        ) limit: Int = 100,
        @Parameter(name = "afterDate", example = "2023-11-01")
        @RequestParam(
            required = false,
        )
        afterDate: LocalDate?,
        @Parameter(name = "beforeDate", example = "2023-12-31")
        @RequestParam(
            required = false,
        )
        beforeDate: LocalDate?,
    ): List<Map<String, String?>> =
        dokumentHendelseLytter
            .oppdaterStatusPaFerdigstilteDokumenter(limit, afterDate?.atStartOfDay(), beforeDate?.atStartOfDay())
            .map { it.mapToResponse() }

    @GetMapping("/ettersendingIkkeOpprettet")
    @Operation(
        summary = "Sjekk status på dokumentene i forsendelser og oppdater status hvis det er ute av synk",
        description = """Hent forsendelser som har ettersending som ikke er oppretttet""",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun forsendelserEttersendingIkkeOpprettet(): List<Map<String, String?>> =
        forsendelseTjeneste.hentForsendelserHvorEttersendingIkkeOpprettet().map {
            it.mapToResponse()
        }
}

fun Forsendelse.mapToResponse(): Map<String, String?> {
    val node = mutableMapOf<String, String?>()
    node[Forsendelse::forsendelseId.name] = forsendelseId.toString()
    node[Forsendelse::journalpostIdFagarkiv.name] = journalpostIdFagarkiv
    node[Forsendelse::saksnummer.name] = saksnummer
    node["mottakerIdent"] = mottaker?.ident
    node[Forsendelse::enhet.name] = enhet
    node[Forsendelse::distribuertAvIdent.name] = distribuertAvIdent
    node[Forsendelse::opprettetAvNavn.name] = opprettetAvNavn
    node[Forsendelse::distribuertTidspunkt.name] = distribuertTidspunkt.toString()
    node[Forsendelse::distribusjonKanal.name] = distribusjonKanal?.name
    node[Forsendelse::status.name] = status?.name
    node["ettersending_tittel"] = ettersendingsoppgave?.tittel
    node["ettersending_frist"] = ettersendingsoppgave?.innsendingsfristDager?.toString()
    node["ettersending_skjemaId"] = ettersendingsoppgave?.skjemaId
    return node.mapValues { it.value ?: "" }
}

fun Dokument.mapToResponse(): Map<String, String?> {
    val node = mutableMapOf<String, String?>()
    node[Dokument::tittel.name] = tittel
    node[Dokument::dokumentStatus.name] = dokumentStatus.name
    node[Dokument::dokumentreferanse.name] = dokumentreferanse
    node[Dokument::dokumentreferanseFagarkiv.name] = dokumentreferanseFagarkiv
    node[Forsendelse::journalpostIdFagarkiv.name] = forsendelse.journalpostIdFagarkiv
    node[Forsendelse::forsendelseId.name] = forsendelse.forsendelseId.toString()
    node[Forsendelse::forsendelseType.name] = forsendelse.forsendelseType.name
    node["forsendelseStatus"] = forsendelse.status.name
    node["saksnummer"] = forsendelse.saksnummer
    return node.mapValues { it.value ?: "" }
}
