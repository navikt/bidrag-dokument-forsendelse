package no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity(name = "adresse")
data class Adresse(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val adresselinje1: String,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val bruksenhetsnummer: String? = null,
    val landkode: String? = null,
    val landkode3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
)
