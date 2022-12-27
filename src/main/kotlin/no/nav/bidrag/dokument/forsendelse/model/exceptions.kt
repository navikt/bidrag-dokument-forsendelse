package no.nav.bidrag.dokument.forsendelse.model

class KunneIkkBestilleDokument(melding: String): RuntimeException(melding)
class UgyldigForespørsel(melding: String): RuntimeException(melding)
class UgyldigEndringAvForsendelse(melding: String): RuntimeException(melding)
class KanIkkeFerdigstilleForsendelse(melding: String): RuntimeException(melding)