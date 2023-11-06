package no.nav.bidrag.dokument.forsendelse.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadType
import no.nav.bidrag.domene.enums.Engangsbeløptype
import no.nav.bidrag.domene.enums.Stønadstype
import no.nav.bidrag.domene.enums.SøktAvType
import no.nav.bidrag.domene.enums.Vedtakstype

data class HentDokumentValgRequest(
    @Schema(enumAsRef = true) val soknadType: SoknadType? = null,
    @Schema(enumAsRef = true) val vedtakType: Vedtakstype? = null,
    @Schema(enumAsRef = true) val behandlingType: BehandlingType? = null,
    @Schema(enumAsRef = true) val soknadFra: SøktAvType? = null,
    val erFattetBeregnet: Boolean? = null,
    val erVedtakIkkeTilbakekreving: Boolean? = false,
    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val enhet: String? = null,
    @Schema(enumAsRef = true) val stonadType: Stønadstype? = null,
    @Schema(enumAsRef = true) val engangsBelopType: Engangsbeløptype? = null,
) {
    fun erKlage() = vedtakType == Vedtakstype.KLAGE || soknadType == Vedtakstype.KLAGE.name
}

fun HentDokumentValgRequest.tilBehandlingInfo(): BehandlingInfo = BehandlingInfo(
    vedtakId = this.vedtakId,
    behandlingId = this.behandlingId,
    vedtakType = this.vedtakType,
    engangsBelopType = this.engangsBelopType,
    stonadType = this.stonadType,
    soknadType = this.soknadType,
    erFattetBeregnet = this.erFattetBeregnet,
    erVedtakIkkeTilbakekreving = this.erVedtakIkkeTilbakekreving,
    soknadFra = this.soknadFra,
    behandlingType = this.behandlingType,
)