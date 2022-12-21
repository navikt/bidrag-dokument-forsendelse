package no.nav.bidrag.dokument.forsendelse.persistence.entity

import no.nav.bidrag.dokument.forsendelse.persistence.model.MottakerIdentType
import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.OneToOne

@Entity(name = "mottaker")
data class Mottaker (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val ident: String? = null,
    @Enumerated(EnumType.STRING)
    val identType: MottakerIdentType? = null,
    val navn: String? = null,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val adresse: Adresse? = null
)