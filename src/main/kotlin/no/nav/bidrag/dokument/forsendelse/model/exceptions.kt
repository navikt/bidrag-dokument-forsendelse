package no.nav.bidrag.dokument.forsendelse.model

class UgyldigForespørsel(melding: String): RuntimeException(melding)
class UgyldigEndringAvForsendelse(melding: String): RuntimeException(melding)