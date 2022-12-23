package no.nav.bidrag.dokument.forsendelse.model

const val FORSENDELSEID_PREFIX = "BIF"

typealias FORSENDELSE_ID = String
typealias Dokumentreferanse = String
val Dokumentreferanse.utenPrefiks get() = this.replace("\\D".toRegex(), "")
val Dokumentreferanse.erForsendelseDokument get() = this.startsWith(FORSENDELSEID_PREFIX)
val FORSENDELSE_ID.numerisk get() =  this.replace("\\D".toRegex(), "").toLong()