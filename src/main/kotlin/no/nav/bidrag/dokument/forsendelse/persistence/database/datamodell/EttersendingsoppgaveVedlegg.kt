package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
open class EttersendingsoppgaveVedlegg(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ettersendingsoppgave_id", nullable = false)
    open val ettersendingsoppgave: Ettersendingsoppgave,
    open var tittel: String = "",
    open var url: String? = null,
    open var skjemaId: String? = null,
    open var opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
) {
    override fun toString(): String =
        "EttersendingsoppgaveVedlegg(id=$id, ettersendingsoppgave=${ettersendingsoppgave.id}, tittel=$tittel, url=$url, skjemaId=$skjemaId)"
}
