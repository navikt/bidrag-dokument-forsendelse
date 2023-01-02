package no.nav.bidrag.dokument.forsendelse.model

class KunneIkkBestilleDokument(melding: String): RuntimeException(melding)
class UgyldigForespørsel(melding: String): RuntimeException(melding)
class UgyldigAvvikForForsendelse(melding: String): RuntimeException(melding)
class UgyldigEndringAvForsendelse(melding: String): RuntimeException(melding)
class KanIkkeFerdigstilleForsendelse(melding: String): RuntimeException(melding)
class FantIkkeDokument(melding: String): RuntimeException(melding)
class FantIkkeForsendelse(forsendelseId: Long): RuntimeException("Fant ikke forsendelse med forsendelseId=$forsendelseId")
class KanIkkeDistribuereForsendelse(forsendelseId: Long): RuntimeException("Kunne ikke distribuere forsendelseId=$forsendelseId")