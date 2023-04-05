package no.nav.bidrag.dokument.forsendelse.database.datamodell

import com.fasterxml.jackson.databind.ObjectMapper
import com.vladmihalcea.hibernate.type.ImmutableType
import com.vladmihalcea.hibernate.type.util.Configuration
import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentDetaljer
import no.nav.bidrag.dokument.forsendelse.database.model.*
import no.nav.bidrag.dokument.forsendelse.model.toStringByReflection
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.type.spi.TypeBootstrapContext
import org.springframework.data.util.ProxyUtils
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import javax.persistence.*

private val log = KotlinLogging.logger {}

@Entity(name = "dokument")
@Table(
    name = "dokument", uniqueConstraints = [
        UniqueConstraint(columnNames = ["journalpostIdOriginal", "dokumentreferanseOriginal", "forsendelse_id"]),
    ]
)
@TypeDef(name = "hstore", typeClass = DokumentMetadataDoConverter::class)
data class Dokument(
    @Id
    @GeneratedValue(generator = "sequence-generator")
    @GenericGenerator(
        name = "sequence-generator",
        strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
        parameters = [
            Parameter(name = "sequence_name", value = "dokument_dokument_id_seq"),
            Parameter(name = "initial_value", value = "100000000"),
            Parameter(name = "min_value", value = "100000000"),
            Parameter(name = "increment_size", value = "1")
        ]
    )
    val dokumentId: Long? = null,

    val dokumentreferanseOriginal: String? = null,
    val journalpostIdOriginal: String? = null,
    val tittel: String,
    val språk: String? = null,
    val dokumentmalId: String? = null,
    val dokumentreferanseFagarkiv: String? = null,

    val slettetTidspunkt: LocalDate? = null,
    val opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    val dokumentDato: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    val dokumentStatus: DokumentStatus,

    @Enumerated(EnumType.STRING)
    val arkivsystem: DokumentArkivSystem,

    val rekkefølgeIndeks: Int,

    @Type(type = "hstore")
    @Column(columnDefinition = "hstore", name = "metadata")
    val metadata: DokumentMetadataDo = DokumentMetadataDo(),

    @ManyToOne
    @JoinColumn(name = "forsendelse_id")
    var forsendelse: Forsendelse
) {

    override fun toString(): String {
        return this.toStringByReflection(listOf("forsendelse"))
    }

    val erFraAnnenKilde get() = !(dokumentreferanseOriginal == null && journalpostIdOriginal == null)
    val tilknyttetSom: DokumentTilknyttetSom get() = if (rekkefølgeIndeks == 0) DokumentTilknyttetSom.HOVEDDOKUMENT else DokumentTilknyttetSom.VEDLEGG
    val dokumentreferanse get() = dokumentreferanseOriginal ?: "BIF$dokumentId"

    val journalpostId
        get() = run {
            if (journalpostIdOriginal.isNullOrEmpty()) null
            else if (harJournalpostIdArkivPrefiks()) journalpostIdOriginal
            else when (arkivsystem) {
                DokumentArkivSystem.JOARK -> "JOARK-$journalpostIdOriginal"
                DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> "BID-$journalpostIdOriginal"
                else -> journalpostIdOriginal
            }
        }

    private fun harJournalpostIdArkivPrefiks(): Boolean = journalpostIdOriginal?.contains("-") == true

}

class DokumentMetadataDo : MutableMap<String, String> by hashMapOf() {

    companion object {
        fun from(initValue: Map<String, String> = hashMapOf()): DokumentMetadataDo {
            val dokmap = DokumentMetadataDo()
            dokmap.putAll(initValue)
            return dokmap
        }
    }

    private val REDIGERING_METADATA_KEY = "redigering_metadata"
    private val DOKUMENT_DETALJER_KEY = "dokument_detaljer"
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    fun lagreRedigeringmetadata(data: String) {
        remove(REDIGERING_METADATA_KEY)
        put(REDIGERING_METADATA_KEY, data)
    }

    fun hentRedigeringmetadata(): String? {
        return get(REDIGERING_METADATA_KEY)
    }

    fun lagreDokumentDetaljer(data: DokumentDetaljer) {
        remove(DOKUMENT_DETALJER_KEY)
        put(DOKUMENT_DETALJER_KEY, objectMapper.writeValueAsString(data))
    }

    fun hentDokumentDetaljer(): DokumentDetaljer? {
        return get(DOKUMENT_DETALJER_KEY)?.let { objectMapper.readValue(it, DokumentDetaljer::class.java) }
    }


    fun copy(): DokumentMetadataDo {
        return from(this)
    }
}


class DokumentMetadataDoConverter(typeBootstrapContext: TypeBootstrapContext) : ImmutableType<DokumentMetadataDo>(
    DokumentMetadataDo::class.java,
    Configuration(typeBootstrapContext.configurationSettings)
) {
    override fun sqlTypes(): IntArray {
        return intArrayOf(Types.OTHER)
    }

    override fun get(rs: ResultSet, names: Array<out String>?, session: SharedSessionContractImplementor?, owner: Any): DokumentMetadataDo {
        val map = names?.let { rs.getObject(names[0]) as Map<String, String>? } ?: emptyMap()
        return DokumentMetadataDo.from(map)
    }

    override fun set(st: PreparedStatement, value: DokumentMetadataDo?, index: Int, session: SharedSessionContractImplementor) {
        st.setObject(index, value?.toMap())
    }
}

@MappedSuperclass
abstract class BaseEntity<T : Serializable> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: T? = null

    override fun equals(other: Any?): Boolean {
        other ?: return false

        if (this === other) return true

        if (javaClass != ProxyUtils.getUserClass(other)) return false

        other as BaseEntity<*>

        return this.id != null && this.id == other.id
    }

    override fun hashCode() = 25

    override fun toString(): String {
        return "${this.javaClass.simpleName}(id=$id)"
    }
}