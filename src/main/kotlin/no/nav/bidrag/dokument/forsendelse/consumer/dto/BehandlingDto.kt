package no.nav.bidrag.dokument.forsendelse.consumer.dto

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype

data class BehandlingDto(
    val behandlingType: BehandlingType,
    val soknadType: Vedtakstype,
    val soknadFraType: SøktAvType,
    val saksnummer: String,
    val behandlerEnhet: String,
    @JsonProperty("aarsak")
    val aarsakKode: String? = null
)
