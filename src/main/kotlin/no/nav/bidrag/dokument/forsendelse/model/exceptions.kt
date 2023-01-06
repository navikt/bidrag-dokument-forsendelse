package no.nav.bidrag.dokument.forsendelse.model

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

class KunneIkkBestilleDokument(melding: String): RuntimeException(melding)
class UgyldigForespørsel(melding: String): RuntimeException(melding)
class UgyldigAvvikForForsendelse(melding: String): RuntimeException(melding)
class UgyldigEndringAvForsendelse(melding: String): RuntimeException(melding)
class KanIkkeFerdigstilleForsendelse(melding: String): RuntimeException(melding)
class FantIkkeDokument(melding: String): RuntimeException(melding)
class FantIkkeForsendelse(forsendelseId: Long): RuntimeException("Fant ikke forsendelse med forsendelseId=$forsendelseId")
class KanIkkeDistribuereForsendelse(forsendelseId: Long): RuntimeException("Kunne ikke distribuere forsendelseId=$forsendelseId")


fun kanIkkeDistribuereForsendelse(forsendelseId: Long): Nothing = throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Kan ikke distribuere forsendelse $forsendelseId")