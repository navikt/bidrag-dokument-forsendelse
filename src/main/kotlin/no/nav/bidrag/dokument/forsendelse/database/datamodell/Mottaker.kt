package no.nav.bidrag.dokument.forsendelse.database.datamodell

import no.nav.bidrag.dokument.forsendelse.database.model.MottakerIdentType
import javax.persistence.*

@Entity(name = "mottaker")
data class Mottaker(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val ident: String? = null,
    @Enumerated(EnumType.STRING)
    val identType: MottakerIdentType? = null,
    val navn: String? = null,
    val språk: String? = null,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val adresse: Adresse? = null
)