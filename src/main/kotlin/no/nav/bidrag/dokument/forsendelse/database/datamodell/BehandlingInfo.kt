package no.nav.bidrag.dokument.forsendelse.database.datamodell

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity(name = "behandling_info")
data class BehandlingInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val vedtakId: String? = null,
    val behandlingId: String? = null
)
