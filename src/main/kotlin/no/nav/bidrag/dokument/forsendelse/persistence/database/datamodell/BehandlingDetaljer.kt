package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

//@Entity(name = "behandling_detaljer")
data class BehandlingDetaljer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val behandlingId: String,
    val vedtakId: String? = null,
)
