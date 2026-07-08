package no.nav.bidrag.dokument.forsendelse.consumer.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class BehandlingDto(
    val id: Long,
    val vedtakstype: Vedtakstype,
    val stønadstype: Stønadstype? = null,
    val engangsbeløptype: Engangsbeløptype? = null,
    val erVedtakFattet: Boolean = false,
    val erKlageEllerOmgjøring: Boolean = false,
    val opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val søktFomDato: LocalDate = LocalDate.now(),
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val mottattdato: LocalDate = LocalDate.now(),
    val søktAv: SøktAvType,
    val saksnummer: String,
    val søknadsid: Long,
    val søknadRefId: Long? = null,
    val vedtakRefId: Long? = null,
    val behandlerenhet: String,
    @Schema(type = "string", format = "date", example = "01.12.2025")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val virkningstidspunkt: LocalDate? = null,
    @get:Schema(name = "årsak")
    val årsak: VirkningstidspunktÅrsakstype? = null,
    @get:Schema(enumAsRef = true)
    val avslag: Resultatkode? = null,
    val roller: Set<RolleDto>,
) {
    fun finnSøknadsbarnForSøknad(søknadsId: Long) =
        if (erKlageEllerOmgjøring) {
        } else {
            søknadsbarn.filter {
                it.søknader.any {
                    it.søknadsId == søknadsId
                }
            }
        }

    val søknadsbarn get() = roller.filter { it.rolletype == Rolletype.BARN }
}

data class RolleDto(
    val id: Long,
    val rolletype: Rolletype,
    val ident: String? = null,
    val navn: String? = null,
    val fødselsdato: LocalDate? = null,
    val harInnvilgetTilleggsstønad: Boolean? = null,
    val delAvOpprinneligBehandling: Boolean?,
    val erRevurdering: Boolean?,
    val stønadstype: Stønadstype?,
    val saksnummer: String,
    val beregnFraDato: YearMonth? = null,
    val beregnTilDato: YearMonth? = null,
    val bidragsmottaker: String? = null,
    val harLøpendeForskudd: Boolean? = false,
    val harLøpendeBidrag: Boolean? = false,
    val søknader: List<RolleSøknadDto> = emptyList(),
) {
    val erOver18År get() = fødselsdato?.let { LocalDate.now().minusYears(18).isAfter(it) } ?: false
}

data class RolleSøknadDto(
    val søknadsId: Long,
    val søknadFra: SøktAvType,
    val enhet: String,
    val vedtakstype: Vedtakstype,
)
