package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.MottakerIdentType

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
