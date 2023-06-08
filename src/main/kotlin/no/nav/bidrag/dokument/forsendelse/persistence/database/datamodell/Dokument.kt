package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.hypersistence.utils.hibernate.type.ImmutableType
import io.hypersistence.utils.hibernate.type.util.Configuration
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentDetaljer
import no.nav.bidrag.dokument.forsendelse.model.toStringByReflection
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentTilknyttetSom
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.type.spi.TypeBootstrapContext
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Entity(name = "dokument")
@Table(
    name = "dokument",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["journalpostIdOriginal", "dokumentreferanseOriginal", "forsendelse_id"])
    ]
)
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

    @Type(DokumentMetadataDoConverter::class)
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
    val erLenkeTilEnAnnenForsendelse get() = erFraAnnenKilde && arkivsystem == DokumentArkivSystem.FORSENDELSE
    val tilknyttetSom: DokumentTilknyttetSom get() = if (rekkefølgeIndeks == 0) DokumentTilknyttetSom.HOVEDDOKUMENT else DokumentTilknyttetSom.VEDLEGG
    val dokumentreferanse get() = "BIF$dokumentId"
    val filsti get() = "forsendelse_${forsendelse.forsendelseId}/dokument_$dokumentreferanse.pdf"

    val forsendelseId get() = if (arkivsystem == DokumentArkivSystem.FORSENDELSE && !journalpostIdOriginal.isNullOrEmpty()) journalpostIdOriginal.toLong() else forsendelse.forsendelseId
    val lenkeTilDokumentreferanse get() = if (arkivsystem == DokumentArkivSystem.FORSENDELSE && !dokumentreferanseOriginal.isNullOrEmpty()) dokumentreferanseOriginal else null
    val journalpostId
        get() = run {
            if (journalpostIdOriginal.isNullOrEmpty()) {
                null
            } else if (harJournalpostIdArkivPrefiks()) {
                journalpostIdOriginal
            } else {
                when (arkivsystem) {
                    DokumentArkivSystem.JOARK -> "JOARK-$journalpostIdOriginal"
                    DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> "BID-$journalpostIdOriginal"
                    else -> journalpostIdOriginal
                }
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

    fun lagreDokumentDetaljer(data: List<DokumentDetaljer>) {
        remove(DOKUMENT_DETALJER_KEY)
        put(DOKUMENT_DETALJER_KEY, objectMapper.writeValueAsString(data))
    }

    fun hentDokumentDetaljer(): List<DokumentDetaljer>? {
        return get(DOKUMENT_DETALJER_KEY)?.let { objectMapper.readValue(it, object : TypeReference<List<DokumentDetaljer>?>() {}) }
    }

    fun copy(): DokumentMetadataDo {
        return from(this)
    }
}

class DokumentMetadataDoConverter(typeBootstrapContext: TypeBootstrapContext) : ImmutableType<DokumentMetadataDo>(
    DokumentMetadataDo::class.java,
    Configuration(typeBootstrapContext.configurationSettings)
) {

    override fun get(rs: ResultSet, p1: Int, session: SharedSessionContractImplementor?, owner: Any?): DokumentMetadataDo {
        val map = rs.getObject(p1) as Map<String, String>
        return DokumentMetadataDo.from(map)
    }

    override fun set(st: PreparedStatement, value: DokumentMetadataDo?, index: Int, session: SharedSessionContractImplementor) {
        st.setObject(index, value?.toMap())
    }

    override fun getSqlType(): Int {
        return Types.OTHER
    }
}