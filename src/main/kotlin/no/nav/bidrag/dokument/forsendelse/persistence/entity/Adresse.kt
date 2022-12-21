package no.nav.bidrag.dokument.forsendelse.persistence.entity

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity(name = "adresse")
data class Adresse (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val adresselinje1: String,
    val adresselinje2: String,
    val adresselinje3: String,
    val bruksenhetsnummer: String,
    val landkode: String,
    val postnummer: String,
    val poststed: String
)