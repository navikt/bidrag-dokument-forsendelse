package no.nav.bidrag.dokument.forsendelse.model

private val SAMHANDLER_PATTERN = "^[8-9][0-9]{10}$".toRegex()
private val PERSON_PATTERN = "^[0-7][0-9]{10}$".toRegex()
private val ORGANISASJON_PATTERN = "^([0-9]{4}:)?([0-9]{9})$".toRegex()

typealias PersonIdent = String

fun PersonIdent.erSamhandler(): Boolean = this.matches(SAMHANDLER_PATTERN)

fun PersonIdent.erPerson(): Boolean = this.matches(PERSON_PATTERN)

fun PersonIdent.erOrganisasjonsnummer(): Boolean = this.matches(ORGANISASJON_PATTERN)
