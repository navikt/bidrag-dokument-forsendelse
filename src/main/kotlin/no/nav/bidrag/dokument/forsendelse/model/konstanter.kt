package no.nav.bidrag.dokument.forsendelse.model

const val FORSENDELSEID_PREFIX = "BIF"

typealias FORSENDELSE_ID = String

val FORSENDELSE_ID.numerisk get() =  this.replace("\\D".toRegex(), "").toLong()