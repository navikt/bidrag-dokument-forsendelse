package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import no.nav.bidrag.behandling.felles.enums.EngangsbelopType
import no.nav.bidrag.behandling.felles.enums.StonadType
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra

@Entity(name = "behandling_info")
data class BehandlingInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val soknadId: String? = null,
    val erFattetBeregnet: Boolean? = null,

    @Enumerated(EnumType.STRING)
    val engangsBelopType: EngangsbelopType? = null,
    @Enumerated(EnumType.STRING)
    val stonadType: StonadType? = null,
    @Enumerated(EnumType.STRING)
    val vedtakType: VedtakType? = null,
    @Enumerated(EnumType.STRING)
    val soknadFra: SoknadFra? = null

    // Bisys koder
//    val soknadGruppe: SoknadGruppe? = null,
//    val soknadType: SoknadType? = null,
//    val soknadFra: SoknadFra? = null,
) {
    fun toBehandlingType(): String? = stonadType?.name ?: engangsBelopType?.name
}
