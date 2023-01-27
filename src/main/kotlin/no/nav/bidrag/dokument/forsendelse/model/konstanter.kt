package no.nav.bidrag.dokument.forsendelse.model

const val FORSENDELSEID_PREFIX = "BIF"

typealias ForsendelseId = String

val ForsendelseId.numerisk get() = this.replace("\\D".toRegex(), "").toLong()