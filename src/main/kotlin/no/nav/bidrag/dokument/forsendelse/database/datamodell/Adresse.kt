package no.nav.bidrag.dokument.forsendelse.database.datamodell

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
    val adresselinje2: String ?= null,
    val adresselinje3: String ?= null,
    val bruksenhetsnummer: String ?= null,
    val landkode: String ?= null,
    val landkode3: String ?= null,
    val postnummer: String ?= null,
    val poststed: String ?= null
)