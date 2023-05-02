package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

//@Entity(name = "behandling_detaljer")
data class BehandlingDetaljer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val behandlingId: String,
    val vedtakId: String? = null,
)
