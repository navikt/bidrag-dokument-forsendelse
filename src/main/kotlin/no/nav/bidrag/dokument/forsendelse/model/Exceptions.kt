package no.nav.bidrag.dokument.forsendelse.model

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

class KunneIkkBestilleDokument(
    melding: String,
) : RuntimeException(melding)

class UgyldigForespørsel(
    melding: String,
) : RuntimeException(melding)

class UgyldigAvvikForForsendelse(
    melding: String,
) : RuntimeException(melding)

class UgyldigEndringAvForsendelse(
    melding: String,
) : RuntimeException(melding)

class KanIkkeFerdigstilleForsendelse(
    melding: String,
) : RuntimeException(melding)

class FantIkkeDokument(
    melding: String,
) : RuntimeException(melding)

class FantIkkeForsendelse(
    forsendelseId: Long,
) : RuntimeException("Fant ikke forsendelse med forsendelseId=$forsendelseId")

class KanIkkeDistribuereForsendelse(
    forsendelseId: Long,
) : RuntimeException("Kunne ikke distribuere forsendelseId=$forsendelseId")

class KunneIkkeLeseMeldingFraHendelse(
    melding: String?,
    throwable: Throwable,
) : RuntimeException(melding, throwable)

class HentVedtakFeiletException(
    melding: String,
    throwable: Throwable,
) : RuntimeException(melding, throwable)

class KunneIkkeFerdigstilleForsendelse(
    forsendelseId: Long,
) : HttpClientErrorException(HttpStatus.BAD_REQUEST, "Det skjedde en feil ved ferdigstilling av forsendelse $forsendelseId")

fun ugyldigEndringAvForsendelse(melding: String): Nothing = throw UgyldigEndringAvForsendelse(melding)

fun hentSakFeilet(saksnummer: String): Nothing = throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Kunne ikke hente sak $saksnummer")

fun kunneIkkeFerdigstilleForsendelse(forsendelseId: Long): Nothing = throw KunneIkkeFerdigstilleForsendelse(forsendelseId)

fun kanIkkeDistribuereForsendelse(
    forsendelseId: Long,
    begrunnelse: String,
): Nothing = throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Kan ikke distribuere forsendelse $forsendelseId: $begrunnelse")

fun distribusjonFeilet(forsendelseId: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Det skjedde en feil ved bestilling av distribusjon av forsendelse $forsendelseId",
    )

fun fantIkkeDokumentfil(
    forsendelseId: Long,
    dokumentreferanse: String,
): Nothing =
    throw HttpClientErrorException(
        HttpStatus.NOT_FOUND,
        "Finnes ingen dokumentfil i bucket for $dokumentreferanse i forsendelse $forsendelseId",
    )

fun fantIkkeDokument(
    forsendelseId: Long,
    dokumentreferanse: String,
): Nothing = throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Finnes ingen dokument $dokumentreferanse i forsendelse $forsendelseId")

fun fantIkkeForsendelse(forsendelseId: Long): Nothing =
    throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Fant ikke forsendelse med id $forsendelseId")

fun forsendelseHarIngenBehandlingInfo(forsendelseId: Long): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Forsendelse med id $forsendelseId har ingen behandlinginfo. Kan ikke hente dokumentvalg",
    )

fun fantIkkeForsendelse(
    forsendelseId: Long,
    saksnummer: String,
): Nothing = throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Fant ikke forsendelse med id $forsendelseId og saksnummer $saksnummer")

fun fantIkkeForsendelseNoContent(forsendelseId: Long): Nothing =
    throw HttpClientErrorException(HttpStatus.NO_CONTENT, "Fant ikke forsendelse med id $forsendelseId")

fun fantIkkeSak(saksnummer: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Sak med saksnummer $saksnummer finnes ikke",
    )

fun ugyldigAvviksForespørsel(melding: String): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Ugyldig avvikforespørsel: $melding",
    )

fun ingenTilgang(message: String): Nothing = throw HttpClientErrorException(HttpStatus.FORBIDDEN, message)
