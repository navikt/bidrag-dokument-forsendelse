package no.nav.bidrag.dokument.forsendelse.database.datamodell

import com.vladmihalcea.hibernate.type.basic.PostgreSQLHStoreType
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity(name = "dokument")
@TypeDef(name = "hstore", typeClass = PostgreSQLHStoreType::class)
data class Dokument (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val dokumentId: Long? = null,

    val journalpostId: String? = null,
    val eksternDokumentreferanse: String? = null,
    val tittel: String,
    val dokumentmalId: String? = null,

    val slettetTidspunkt: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    val dokumentStatus: DokumentStatus,

    @Enumerated(EnumType.STRING)
    val arkivsystem: DokumentArkivSystem,

    @Enumerated(EnumType.STRING)
    val tilknyttetSom: DokumentTilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT,

    @Type(type = "hstore")
    @Column(columnDefinition = "hstore")
    val metadata: Map<String, String> = mapOf(),

    @ManyToOne
    @JoinColumn(name = "forsendelse_id")
    val forsendelse: Forsendelse
){
    val dokumentreferanse get() = eksternDokumentreferanse ?: "BIF_$dokumentId"
}