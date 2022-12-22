package no.nav.bidrag.dokument.forsendelse.database.datamodell

import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.OneToOne

@Entity(name = "forsendelse")
data class Forsendelse (
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
    val status: ForsendelseStatus = ForsendelseStatus.UNDER_PRODUKSJON,
    val enhet: String,
    val saksnummer: String,
    val gjelderIdent: String,
    val opprettetAvIdent: String,
    val opprettetAvNavn: String? = null,
    val distribusjonBestillingsId: String? = null,
    val distribuertTidspunkt: LocalDateTime? = null,
    val ferdigstiltTidspunkt: LocalDateTime? = null,
    val opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    val endretTidspunkt: LocalDateTime = LocalDateTime.now(),
    val arkivJournalpostId: String? = null,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val mottaker: Mottaker? = null,

    @OneToMany(mappedBy = "forsendelse", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    val dokumenter: List<Dokument> = emptyList()
)