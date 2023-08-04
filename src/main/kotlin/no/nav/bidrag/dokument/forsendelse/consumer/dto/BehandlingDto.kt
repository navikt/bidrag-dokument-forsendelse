package no.nav.bidrag.dokument.forsendelse.consumer.dto

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra

data class BehandlingDto(
    val behandlingType: BehandlingType,
    val soknadType: VedtakType,
    val soknadFraType: SoknadFra,
    val saksnummer: String,
    val behandlerEnhet: String,
    @JsonProperty("aarsak")
    val aarsakKode: String? = null
)
