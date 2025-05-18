package no.nav.bidrag.dokument.forsendelse.api.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadType
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import java.time.LocalDateTime

@Schema(description = "Metadata for opprettelse av forsendelse")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettForsendelseForespørsel(
    val unikReferanse: String? = null,
    @Schema(description = "Ident til brukeren som journalposten gjelder") val gjelderIdent: String,
    val mottaker: MottakerTo? = null,
    @Schema(
        description = """
    Dokumenter som skal knyttes til journalpost. 
    En journalpost må minst ha et dokument. 
    Det første dokument i meldingen blir tilknyttet som hoveddokument på journalposten.""",
        required = true,
    )
    val dokumenter: List<OpprettDokumentForespørsel> = emptyList(),
    @Schema(description = "Bidragsak som forsendelse skal tilknyttes") val saksnummer: String,
    @Schema(description = "NAV-enheten som oppretter forsendelsen") val enhet: String,
    @Schema(
        description = "Detaljer om behandling hvis forsendelsen inneholder brev for en behandling eller vedtak",
        enumAsRef = true,
    ) val behandlingInfo: BehandlingInfoDto? = null,
    @Schema(description = "Identifikator til batch kjøring forsendelsen ble opprettet av") val batchId: String? = null,
    @Schema(description = "Tema forsendelsen skal opprettes med", enumAsRef = true) val tema: JournalTema? = null,
    @Schema(description = "Språk forsendelsen skal være på") val språk: String? = null,
    @Schema(
        description =
            "Ident til saksbehandler som oppretter journalpost. " +
                "Dette vil prioriteres over ident som tilhører tokenet til kallet.",
    ) val saksbehandlerIdent: String? = null,
    @Schema(
        description =
            "Opprett tittel på forsendelse automatisk basert på behandling detaljer. " +
                "Skal bare settes til false hvis gamle brevmeny (Bisys) brukes",
    ) val opprettTittel: Boolean? = false,
)

data class BehandlingInfoDto(
    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val soknadId: String? = null,
    @Schema(enumAsRef = true) val engangsBelopType: Engangsbeløptype? = null,
    @Schema(enumAsRef = true) val stonadType: Stønadstype? = null,
    @Schema(description = "Brukes bare hvis stonadType og engangsbelopType er null") val behandlingType: BehandlingType? = null,
    @Schema(enumAsRef = true) val vedtakType: Vedtakstype? = null,
    @Schema(
        enumAsRef = true,
        description = "Soknadtype er gamle kodeverdier som er erstattet av vedtaktype.",
    ) val soknadType: SoknadType? = null,
    val erFattetBeregnet: Boolean? = null,
    @Schema(description = "Hvis resultatkoden fra BBM er IT så skal denne være sann") val erVedtakIkkeTilbakekreving: Boolean? = null,
    @Schema(enumAsRef = true) val soknadFra: SøktAvType? = null,
    val barnIBehandling: List<String> = emptyList(),
)

@Schema(description = "Metadata til en respons etter forsendelse ble opprettet")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettForsendelseRespons(
    @Schema(description = "ForsendelseId på forsendelse som ble opprettet") val forsendelseId: Long? = null,
    val forsendelseType: ForsendelseTypeTo? = null,
    @Schema(description = "Liste med dokumenter som er knyttet til journalposten") val dokumenter: List<DokumentRespons> = emptyList(),
)

@Schema(
    description =
        "Metadata for dokument som skal knyttes til forsendelsen. " +
            "Første dokument i listen blir automatisk satt som hoveddokument i forsendelsen",
)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpprettDokumentForespørsel(
    @Schema(description = "Dokumentets tittel") override val tittel: String = "",
    @Schema(description = "Språket på inneholdet i dokumentet.") val språk: String? = null,
    @Schema(description = "Arkivsystem hvor dokument er lagret", enumAsRef = true) override val arkivsystem: DokumentArkivSystemDto? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd.MM.yyyy-HH-mm-ss")
    @Schema(description = "Dato dokument ble opprettet")
    override val dokumentDato: LocalDateTime? = null,
    @Schema(
        description =
            "Referansen til dokumentet hvis det er allerede er lagret i arkivsystem. " +
                "Hvis dette ikke settes opprettes det en ny dokumentreferanse som kan brukes ved opprettelse av dokument",
    ) override val dokumentreferanse: String? = null,
    @Schema(description = "JournalpostId til dokumentet hvis det er allerede er lagret i arkivsystem") override val journalpostId:
        JournalpostId? = null,
    @Schema(
        description = "DokumentmalId sier noe om dokumentets innhold og oppbygning. (Også kjent som brevkode)",
    ) override val dokumentmalId: String? = null,
    @Schema(
        description =
            "Om dokumentet med dokumentmalId skal bestilles. " +
                "Hvis dette er satt til false så antas det at kallende system bestiller dokumentet selv.",
    ) val bestillDokument: Boolean = true,
) : DokumentForespørsel()
