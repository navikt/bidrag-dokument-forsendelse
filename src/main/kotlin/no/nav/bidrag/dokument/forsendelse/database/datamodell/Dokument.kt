package no.nav.bidrag.dokument.forsendelse.database.datamodell

import com.vladmihalcea.hibernate.type.basic.PostgreSQLHStoreType
import no.nav.bidrag.dokument.forsendelse.database.model.*
import no.nav.bidrag.dokument.forsendelse.model.toStringByReflection
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import java.time.LocalDate
import javax.persistence.*


@Entity(name = "dokument")
@Table(
    name = "dokument", uniqueConstraints = [
        UniqueConstraint(columnNames = ["journalpostIdOriginal", "dokumentreferanseOriginal", "forsendelse_id"]),
    ]
)
@TypeDef(name = "hstore", typeClass = PostgreSQLHStoreType::class)
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

    @Enumerated(EnumType.STRING)
    val dokumentStatus: DokumentStatus,

    @Enumerated(EnumType.STRING)
    val arkivsystem: DokumentArkivSystem,

    val rekkefølgeIndeks: Int,

    @Type(type = "hstore")
    @Column(columnDefinition = "hstore")
    val metadata: Map<String, String> = mapOf(),

    @ManyToOne
    @JoinColumn(name = "forsendelse_id")
    var forsendelse: Forsendelse
) {

    override fun toString(): String {
        return this.toStringByReflection(listOf("forsendelse"))
    }

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