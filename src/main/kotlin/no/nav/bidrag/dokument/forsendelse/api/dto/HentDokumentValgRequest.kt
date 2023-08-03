package no.nav.bidrag.dokument.forsendelse.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadType

data class HentDokumentValgRequest(
    @Schema(enumAsRef = true) val soknadType: SoknadType? = null,
    @Schema(enumAsRef = true) val vedtakType: VedtakType? = null,
    @Schema(enumAsRef = true) val behandlingType: BehandlingType? = null,
    @Schema(enumAsRef = true) val soknadFra: SoknadFra? = null,
    val erFattetBeregnet: Boolean? = null,
    val erVedtakIkkeTilbakekreving: Boolean? = false,
    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val enhet: String? = null
)
