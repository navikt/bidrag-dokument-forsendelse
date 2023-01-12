package no.nav.bidrag.dokument.forsendelse.database.datamodell

import com.vladmihalcea.hibernate.type.basic.PostgreSQLHStoreType
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.model.toStringByReflection
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import java.time.LocalDate
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.UniqueConstraint


@Entity(name = "dokument")
@Table(name = "dokument", uniqueConstraints = [
    UniqueConstraint(columnNames = ["journalpostId", "eksternDokumentreferanse", "forsendelse_id"]),
])
@TypeDef(name = "hstore", typeClass = PostgreSQLHStoreType::class)
data class Dokument (
    @Id
    @GeneratedValue(generator = "sequence-generator")
    @GenericGenerator(
        name = "sequence-generator",
        strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
        parameters = [
            Parameter(name = "sequence_name", value = "dokument_dokument_id_seq"),
            Parameter(name = "initial_value", value = "1000000000"),
            Parameter(name = "min_value", value = "1000000000"),
            Parameter(name = "increment_size", value = "1")
        ]
    )
    val dokumentId: Long? = null,

    val journalpostId: String? = null,
    val eksternDokumentreferanse: String? = null,
    val tittel: String,
    val dokumentmalId: String? = null,
    val fagrkivDokumentreferanse: String? = null,

    val slettetTidspunkt: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    val dokumentStatus: DokumentStatus,

    @Enumerated(EnumType.STRING)
    val arkivsystem: DokumentArkivSystem,

    @Enumerated(EnumType.STRING)
    val tilknyttetSom: DokumentTilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT,

    val rekkefølgeIndeks: Int,

    @Type(type = "hstore")
    @Column(columnDefinition = "hstore")
    val metadata: Map<String, String> = mapOf(),

    @ManyToOne
    @JoinColumn(name = "forsendelse_id")
    var forsendelse: Forsendelse
){


    override fun toString(): String {
        return this.toStringByReflection(listOf("forsendelse"))
    }
    val dokumentreferanse get() = eksternDokumentreferanse ?: "BIF$dokumentId"
}