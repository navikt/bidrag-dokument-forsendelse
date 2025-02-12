package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne

@Entity
open class Ettersendingsoppgave(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forsendelse_id", nullable = false)
    open val forsendelse: Forsendelse,
    open var tittel: String? = null,
    open var innsendingsfristDager: Int = 21,
    open var ettersendelseForJournalpostId: String? = null,
    open var innsendingsId: String? = null,
    open var skjemaId: String? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "ettersendingsoppgave",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE],
        orphanRemoval = true,
    )
    open var vedleggsliste: MutableSet<EttersendingsoppgaveVedlegg> = mutableSetOf(),
) {
    override fun toString(): String =
        "Ettersendingsoppgave(id=$id, forsendelse=${forsendelse.forsendelseId}, tittel=$tittel, ettersendelseForJournalpostId=$ettersendelseForJournalpostId, innsendelsesId=$innsendingsId, skjemaId=$skjemaId)"
}
