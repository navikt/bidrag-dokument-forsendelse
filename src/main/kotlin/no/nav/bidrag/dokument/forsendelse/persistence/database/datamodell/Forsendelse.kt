package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import com.fasterxml.jackson.databind.ObjectMapper
import io.hypersistence.utils.hibernate.type.ImmutableType
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
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
            Parameter(name = "increment_size", value = "1"),
        ],
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
    val unikReferanse: String? = null,
    @OneToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        mappedBy = "forsendelse",
        orphanRemoval = true,
    )
    var ettersendingsoppgave: Ettersendingsoppgave? = null,
    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val mottaker: Mottaker? = null,
    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val behandlingInfo: BehandlingInfo? = null,
    @OneToMany(mappedBy = "forsendelse", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    val dokumenter: List<Dokument> = emptyList(),
    // Unik referanseid som kan brukes til sporing av forsendelsen gjennom verdikjeden. Denne verdien brukes som eksternReferanseId når forsendelsen arkiveres i fagarkivet (JOARK)
    // Denne verdien brukes også som duplikatkontroll slik at samme forsendelse ikke opprettes flere ganger i fagarkivet (må være globalt unik verdi)
    val referanseId: String? = null,
    @Type(ForsendelseMetadataDoConverter::class)
    @Column(columnDefinition = "hstore", name = "metadata")
    var metadata: ForsendelseMetadataDo? = null,
)

fun Forsendelse.opprettReferanseId() = "BIF_${forsendelseId}_${opprettetTidspunkt.toEpochSecond(ZoneOffset.UTC)}"

class ForsendelseMetadataDo : MutableMap<String, String> by hashMapOf() {
    companion object {
        val SJEKKET_OM_DIST_TIL_NAVNO_ER_REDISTRIBUERT = "sjekket_navno_redistribusjon_til_sentral_print"

        fun from(initValue: Map<String, String> = hashMapOf()): ForsendelseMetadataDo {
            val dokmap = ForsendelseMetadataDo()
            dokmap.putAll(initValue)
            return dokmap
        }
    }

    @Suppress("ktlint:standard:property-naming")
    // Indeksert i databasen basert på navn til nøkkelen. (sjekk migrering v1.4.3). Ikke endre uten å lage ny indeks
    private val SJEKKET_OM_DIST_TIL_NAVNO_ER_REDISTRIBUERT = "sjekket_navno_redistribusjon_til_sentral_print"

    @Suppress("ktlint:standard:property-naming")
    private val DISTRIBUER_AUTOMATISK = "distribuer_automatisk"
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    fun markerDistribuerAutomatisk() {
        update(DISTRIBUER_AUTOMATISK, "true")
    }

    fun markerSomSjekketNavNoRedistribusjon() {
        update(SJEKKET_OM_DIST_TIL_NAVNO_ER_REDISTRIBUERT, "true")
    }

    fun harSjekketForNavNoRedistribusjon(): Boolean = get(SJEKKET_OM_DIST_TIL_NAVNO_ER_REDISTRIBUERT) == "true"

    fun skalDistribueresAutomatisk(): Boolean = get(DISTRIBUER_AUTOMATISK) == "true"

    private fun update(
        key: String,
        value: String?,
    ) {
        remove(key)
        value?.let { put(key, value) }
    }

    fun copy(): ForsendelseMetadataDo = from(this)
}

class ForsendelseMetadataDoConverter : ImmutableType<ForsendelseMetadataDo>(ForsendelseMetadataDo::class.java) {
    override fun get(
        rs: ResultSet,
        p1: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?,
    ): ForsendelseMetadataDo? {
        val map = rs.getObject(p1) as Map<String, String>?
        return map?.let { ForsendelseMetadataDo.from(it) }
    }

    override fun set(
        st: PreparedStatement,
        value: ForsendelseMetadataDo?,
        index: Int,
        session: SharedSessionContractImplementor,
    ) {
        st.setObject(index, value?.toMap())
    }

    override fun getSqlType(): Int = Types.OTHER

    override fun compare(
        p0: Any?,
        p1: Any?,
        p2: SessionFactoryImplementor?,
    ): Int = 0

    override fun fromStringValue(sequence: CharSequence?): ForsendelseMetadataDo? =
        try {
            sequence?.let { JacksonUtil.fromString(sequence as String, ForsendelseMetadataDo::class.java) }
        } catch (e: Exception) {
            throw IllegalArgumentException(
                String.format(
                    "Could not transform the [%s] value to a Map!",
                    sequence,
                ),
            )
        }
}
