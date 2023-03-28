package no.nav.bidrag.dokument.forsendelse.model

const val FORSENDELSEID_PREFIX = "BIF"
const val BIDRAG_DOKUMENT_FORSENDELSE_APP_ID = "bidrag-dokument-forsendelse"

typealias ForsendelseId = String

val ForsendelseId.numerisk get() = this.replace("\\D".toRegex(), "").toLong()