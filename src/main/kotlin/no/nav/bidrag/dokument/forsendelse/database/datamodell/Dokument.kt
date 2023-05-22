package no.nav.bidrag.dokument.forsendelse.database.datamodell

import io.hypersistence.utils.hibernate.type.basic.PostgreSQLHStoreType
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
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.model.toStringByReflection
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import java.time.LocalDate
import java.time.LocalDateTime

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

    @Type(PostgreSQLHStoreType::class)
    @Column(columnDefinition = "hstore")
    val metadata: Map<String, String> = mapOf(),

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

class Metadata : Map<String, String> by mapOf()
