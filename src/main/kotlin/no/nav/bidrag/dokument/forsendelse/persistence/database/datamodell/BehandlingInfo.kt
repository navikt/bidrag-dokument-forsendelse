package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadType
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType

@Entity(name = "behandling_info")
data class BehandlingInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val soknadId: String? = null,
    val erFattetBeregnet: Boolean? = null, // Null = ikke fattet, true = fattet og beregnet, false = fattet og manuelt beregnet
    val erVedtakIkkeTilbakekreving: Boolean? = false, // Annen brevmeny vises hvis resultatkode = IT (Vedtak ikke tilbakekreving)

    @Enumerated(EnumType.STRING)
    val engangsBelopType: EngangsbelopType? = null,
    @Enumerated(EnumType.STRING)
    val stonadType: StonadType? = null,
    @Enumerated(EnumType.STRING)
    val vedtakType: VedtakType? = null,
    @Enumerated(EnumType.STRING)
    val soknadFra: SoknadFra? = null,
    // Brukes hvis søknadgruppe fra bisys ikke mapper til stonadType eller engangsbelopType
    // Gjelder foreløpig for soknadGruppe AVSKRIVNING
    val behandlingType: BehandlingType? = null,
    val soknadType: SoknadType? = null
) {
    fun toBehandlingType(): String? = behandlingType ?: stonadType?.name ?: engangsBelopType?.name
}
