package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.hypersistence.utils.hibernate.type.ImmutableType
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentDetaljer
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.engine.spi.SharedSessionContractImplementor
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity(name = "forsendelse")
data class Forsendelse(
    @Id
    @GeneratedValue(generator = "sequence-generator")
    @GenericGenerator(
        name = "sequence-generator",
        strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
        parameters = [
            Parameter(name = "sequence_name", value = "forsendelse_forsendelse_id_seq"),
            Parameter(name = "initial_value", value = "1000000000"),
            Parameter(name = "min_value", value = "1000000000"),
            Parameter(name = "increment_size", value = "1")
        ]
    )
    val forsendelseId: Long? = null,
    @Enumerated(EnumType.STRING)
    val forsendelseType: ForsendelseType,
    @Enumerated(EnumType.STRING)
    val status: ForsendelseStatus = ForsendelseStatus.UNDER_PRODUKSJON,
    @Enumerated(EnumType.STRING)
    val tema: ForsendelseTema = ForsendelseTema.BID,
    val enhet: String,
    @Enumerated(EnumType.STRING)
    val distribusjonKanal: DistribusjonKanal? = null,
    val bestiltNyDistribusjon: Boolean = false,
    val språk: String,
    val saksnummer: String,
    val gjelderIdent: String,
    val opprettetAvIdent: String,
    val endretAvIdent: String,
    val tittel: String? = null,

    val batchId: String? = null,
    val opprettetAvNavn: String? = null,
    val avbruttAvIdent: String? = null,
    val distribuertAvIdent: String? = null,
    val distribusjonBestillingsId: String? = null,
    val distribuertTidspunkt: LocalDateTime? = null,
    val ferdigstiltTidspunkt: LocalDateTime? = null,
    val avbruttTidspunkt: LocalDateTime? = null,
    val opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    val endretTidspunkt: LocalDateTime = LocalDateTime.now(),
    val journalpostIdFagarkiv: String? = null,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val mottaker: Mottaker? = null,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val behandlingInfo: BehandlingInfo? = null,

    @OneToMany(mappedBy = "forsendelse", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    val dokumenter: List<Dokument> = emptyList(),
    // Unik referanseid som kan brukes til sporing av forsendelsen gjennom verdikjeden. Denne verdien brukes som eksternReferanseId når forsendelsen arkiveres i fagarkivet (JOARK)
    // Denne verdien brukes også som duplikatkontroll slik at samme forsendelse ikke opprettes flere ganger i fagarkivet (må være globalt unik verdi)
    val referanseId: String? = null
//
//    @Type(DokumentMetadataDoConverter::class)
//    @Column(columnDefinition = "hstore", name = "metadata")
//    val metadata: DokumentMetadataDo = DokumentMetadataDo(),
)

fun Forsendelse.opprettReferanseId() = "BIF_${forsendelseId}_${opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)}"
class ForsendelseMetadataDo : MutableMap<String, String> by hashMapOf() {

    companion object {
        fun from(initValue: Map<String, String> = hashMapOf()): DokumentMetadataDo {
            val dokmap = DokumentMetadataDo()
            dokmap.putAll(initValue)
            return dokmap
        }
    }

    private val GCP_FILE_PATH_KEY = "gcp_file_path"
    private val GCP_CLIENTSIDE_ENCRYPTION_KEY_VERSION = "gcp_clientside_encryption_key_version"
    private val REDIGERING_METADATA_KEY = "redigering_metadata"
    private val DOKUMENT_DETALJER_KEY = "dokument_detaljer"
    private val DOKUMENT_BESTILT_TIDSPUNKT = "dokument_bestilt_tidspunkt"
    private val ER_STATISK_DOKUMENT = "er_statisk_dokument"
    private val ER_SKJEMA = "er_skjema"
    private val DOKUMENT_PRODUSERT_TIDSPUNKT = "dokument_produsert_tidspunkt"
    private val DOKUMENT_BESTILT_ANTALL_GANGER = "dokument_bestilt_antall_ganger"
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    fun markerSomStatiskDokument() {
        update(ER_STATISK_DOKUMENT, "true")
    }

    fun markerSomSkjema() {
        update(ER_SKJEMA, "true")
    }

    fun erSkjema() = get(ER_SKJEMA) == "true"
    fun erStatiskDokument() = get(ER_STATISK_DOKUMENT) == "true"

    fun inkrementerBestiltAntallGanger() {
        val antallGanger = hentDokumentBestiltAntallGanger()
        update(DOKUMENT_BESTILT_ANTALL_GANGER, (antallGanger + 1).toString())
    }

    fun hentDokumentBestiltAntallGanger(): Int = get(DOKUMENT_BESTILT_ANTALL_GANGER)?.toInt() ?: 0
    fun lagreProdusertTidspunkt(tidspunkt: LocalDateTime?) = update(DOKUMENT_PRODUSERT_TIDSPUNKT, tidspunkt.toString())
    fun hentProdusertTidspunkt(): LocalDateTime? = get(DOKUMENT_PRODUSERT_TIDSPUNKT)?.let { LocalDateTime.parse(it) }
    fun lagreBestiltTidspunkt(tidspunkt: LocalDateTime?) = update(DOKUMENT_BESTILT_TIDSPUNKT, tidspunkt.toString())
    fun hentBestiltTidspunkt(): LocalDateTime? = get(DOKUMENT_BESTILT_TIDSPUNKT)?.let { LocalDateTime.parse(it) }
    fun lagreGcpFilsti(filsti: String?) = update(GCP_FILE_PATH_KEY, filsti)
    fun hentGcpFilsti(): String? = get(GCP_FILE_PATH_KEY)
    fun lagreGcpKrypteringnøkkelVersjon(versjon: String?) = update(GCP_CLIENTSIDE_ENCRYPTION_KEY_VERSION, versjon)

    fun hentGcpKrypteringnøkkelVersjon(): String? = get(GCP_CLIENTSIDE_ENCRYPTION_KEY_VERSION)

    fun lagreRedigeringmetadata(data: String) = update(REDIGERING_METADATA_KEY, data)

    fun hentRedigeringmetadata(): String? = get(REDIGERING_METADATA_KEY)

    fun lagreDokumentDetaljer(data: List<DokumentDetaljer>) = update(DOKUMENT_DETALJER_KEY, objectMapper.writeValueAsString(data))

    fun hentDokumentDetaljer(): List<DokumentDetaljer>? =
        get(DOKUMENT_DETALJER_KEY)?.let { objectMapper.readValue(it, object : TypeReference<List<DokumentDetaljer>?>() {}) }

    private fun update(key: String, value: String?) {
        remove(key)
        value?.let { put(key, value) }
    }

    fun copy(): DokumentMetadataDo {
        return from(this)
    }
}

class ForsendelseMetadataDoConverter : ImmutableType<DokumentMetadataDo>(DokumentMetadataDo::class.java) {

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

    override fun compare(p0: Any?, p1: Any?, p2: SessionFactoryImplementor?): Int {
        return 0
    }

    override fun fromStringValue(sequence: CharSequence?): DokumentMetadataDo {
        return try {
            JacksonUtil.fromString(sequence as String?, DokumentMetadataDo::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                String.format(
                    "Could not transform the [%s] value to a Map!",
                    sequence
                )
            )
        }
    }
}
