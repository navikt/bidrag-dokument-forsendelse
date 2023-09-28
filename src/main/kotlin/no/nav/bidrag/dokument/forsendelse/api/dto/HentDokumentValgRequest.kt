package no.nav.bidrag.dokument.forsendelse.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadType
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType

data class HentDokumentValgRequest(
    @Schema(enumAsRef = true) val soknadType: SoknadType? = null,
    @Schema(enumAsRef = true) val vedtakType: VedtakType? = null,
    @Schema(enumAsRef = true) val behandlingType: BehandlingType? = null,
    @Schema(enumAsRef = true) val soknadFra: SoknadFra? = null,
    val erFattetBeregnet: Boolean? = null,
    val erVedtakIkkeTilbakekreving: Boolean? = false,
    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val enhet: String? = null,
    @Schema(enumAsRef = true) val stonadType: StonadType? = null,
    @Schema(enumAsRef = true) val engangsBelopType: EngangsbelopType? = null,
) {
    fun erKlage() = vedtakType == VedtakType.KLAGE || soknadType == VedtakType.KLAGE.name
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