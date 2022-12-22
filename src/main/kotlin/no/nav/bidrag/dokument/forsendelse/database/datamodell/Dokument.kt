package no.nav.bidrag.dokument.forsendelse.database.datamodell

import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity(name = "dokument")
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

    @ManyToOne
    @JoinColumn(name = "forsendelse_id")
    val forsendelse: Forsendelse
){
    val dokumentreferanse get() = eksternDokumentreferanse ?: "BIF_$dokumentId"
}