package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import io.hypersistence.utils.hibernate.type.ImmutableType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadType
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.hibernate.annotations.Type
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.engine.spi.SharedSessionContractImplementor
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

@Entity(name = "behandling_info")
data class BehandlingInfo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val vedtakId: String? = null,
    val behandlingId: String? = null,
    val soknadId: String? = null,
    // Null = ikke fattet, true = fattet og beregnet, false = fattet og manuelt beregnet
    val erFattetBeregnet: Boolean? = null,
    // Annen brevmeny vises hvis resultatkode = IT (Vedtak ikke tilbakekreving)
    val erVedtakIkkeTilbakekreving: Boolean? = false,
    @Enumerated(EnumType.STRING)
    val engangsBelopType: Engangsbeløptype? = null,
    @Enumerated(EnumType.STRING)
    val stonadType: Stønadstype? = null,
    @Enumerated(EnumType.STRING)
    val vedtakType: Vedtakstype? = null,
    @Enumerated(EnumType.STRING)
    val soknadFra: SøktAvType? = null,
    // Brukes hvis søknadgruppe fra bisys ikke mapper til stonadType eller engangsbelopType
    // Gjelder foreløpig for soknadGruppe AVSKRIVNING
    val behandlingType: BehandlingType? = null,
    val soknadType: SoknadType? = null,
    @Type(BarnIBehandlingConverter::class)
    @Column(name = "barn_i_behandling", columnDefinition = "text")
    val barnIBehandling: BarnIBehandling = BarnIBehandling(),
) {
    fun toBehandlingType(): String? = behandlingType ?: stonadType?.name ?: engangsBelopType?.name
}

class BarnIBehandling : MutableList<String> by mutableListOf() {
    fun asString() = this.distinct().joinToString(",")

    companion object {
        fun from(list: List<String>?): BarnIBehandling {
            val barnIBehandling = BarnIBehandling()
            list?.distinct()?.forEach {
                barnIBehandling.add(it)
            }
            return barnIBehandling
        }

        fun fromString(values: String) = from(values.split(","))
    }
}

class BarnIBehandlingConverter : ImmutableType<BarnIBehandling>(BarnIBehandling::class.java) {
    override fun get(
        rs: ResultSet,
        p1: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?,
    ): BarnIBehandling =
        rs.getObject(p1)?.let {
            BarnIBehandling.fromString(it as String)
        } ?: BarnIBehandling()

    override fun set(
        st: PreparedStatement,
        value: BarnIBehandling?,
        index: Int,
        session: SharedSessionContractImplementor,
    ) {
        st.setObject(index, if (value.isNullOrEmpty()) null else value.asString())
    }

    override fun getSqlType(): Int = Types.OTHER

    override fun compare(
        p0: Any?,
        p1: Any?,
        p2: SessionFactoryImplementor?,
    ): Int = 0

    override fun fromStringValue(sequence: CharSequence?): BarnIBehandling =
        try {
            BarnIBehandling.fromString(sequence as String)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                String.format(
                    "Could not transform the [%s] value to BarnIBehandling!",
                    sequence,
                ),
            )
        }
}
