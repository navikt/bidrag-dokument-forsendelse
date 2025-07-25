package no.nav.bidrag.dokument.forsendelse.aop

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.model.FantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.model.KanIkkeFerdigstilleForsendelse
import no.nav.bidrag.dokument.forsendelse.model.KunneIkkBestilleDokument
import no.nav.bidrag.dokument.forsendelse.model.UgyldigEndringAvForsendelse
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.transport.felles.ifTrue
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

private val LOGGER = KotlinLogging.logger {}

@RestControllerAdvice
class DefaultRestControllerAdvice {
    @ResponseBody
    @ExceptionHandler(
        value = [
            IllegalArgumentException::class, MethodArgumentTypeMismatchException::class,
            ConversionFailedException::class, HttpMessageNotReadableException::class,
        ],
    )
    fun handleInvalidValueExceptions(exception: Exception): ResponseEntity<*> {
        val cause = exception.cause
        val valideringsFeil =
            if (cause is MissingKotlinParameterException) {
                createMissingKotlinParameterViolation(
                    cause,
                )
            } else {
                null
            }
        LOGGER.warn(
            "Forespørselen inneholder ugyldig verdi: ${valideringsFeil ?: "ukjent feil"}",
            exception,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(
                HttpHeaders.WARNING,
                "Forespørselen inneholder ugyldig verdi: ${valideringsFeil ?: exception.message}",
            ).build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(HttpStatusCodeException::class)
    fun handleHttpClientErrorException(exception: HttpStatusCodeException): ResponseEntity<*> {
        val errorMessage = getErrorMessage(exception)
        LOGGER.error(errorMessage, exception)
        val payloadFeilmelding =
            exception.responseBodyAsString.isEmpty().ifTrue { exception.message }
                ?: exception.responseBodyAsString
        return ResponseEntity
            .status(exception.statusCode)
            .header(HttpHeaders.WARNING, errorMessage)
            .body(payloadFeilmelding)
    }

    private fun getErrorMessage(exception: HttpStatusCodeException): String {
        val errorMessage = StringBuilder()
        exception.responseHeaders
            ?.get("Warning")
            ?.let { if (it.size > 0) errorMessage.append(it[0]) }
        if (exception.statusText.isNotNullOrEmpty()) {
            errorMessage.append(exception.statusText)
        }
        return errorMessage.toString()
    }

    private fun createMissingKotlinParameterViolation(ex: MissingKotlinParameterException): String {
        val errorFieldRegex = Regex("\\.([^.]*)\\[\\\"(.*)\"\\]\$")
        val paths =
            ex.path.map { errorFieldRegex.find(it.description)!! }.map {
                val (objectName, field) = it.destructured
                "$objectName.$field"
            }
        return "${paths.joinToString("->")} kan ikke være null"
    }

    @ResponseBody
    @ExceptionHandler(KunneIkkBestilleDokument::class)
    fun kunneIkkBestilleDokument(exception: KunneIkkBestilleDokument): ResponseEntity<*> {
        LOGGER.warn(exception) { "Kunne ikke bestille dokument ${exception.message}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(HttpHeaders.WARNING, "Kunne ikke bestille dokument: ${exception.message}")
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(UgyldigForespørsel::class)
    fun ugyldigForespørsel(exception: UgyldigForespørsel): ResponseEntity<*> {
        LOGGER.warn("Forsendelsen inneholder ugyldig data: ${exception.message}", exception)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(
                HttpHeaders.WARNING,
                "Forespørselen inneholder ugyldig data: ${exception.message}",
            ).build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(UgyldigEndringAvForsendelse::class)
    fun ugyldigEndringAvForsendelse(exception: UgyldigEndringAvForsendelse): ResponseEntity<*> {
        LOGGER.warn("Forsendelsen kan ikke endres: ${exception.message}", exception)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(HttpHeaders.WARNING, exception.message)
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(KanIkkeFerdigstilleForsendelse::class)
    fun kanIkkeFerdigstilleForsendelse(exception: KanIkkeFerdigstilleForsendelse): ResponseEntity<*> {
        LOGGER.warn("Forsendelsen kan ikke ferdigstilles: ${exception.message}", exception)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(
                HttpHeaders.WARNING,
                "Forsendelsen kan ikke ferdigstilles: ${exception.message}",
            ).build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(FantIkkeDokument::class)
    fun fantIkkeDokument(exception: FantIkkeDokument): ResponseEntity<*> {
        LOGGER.warn("Fant ikke dokument: ${exception.message}", exception)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(HttpHeaders.WARNING, "Fant ikke dokument: ${exception.message}")
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(Exception::class)
    fun handleOtherExceptions(exception: Exception): ResponseEntity<*> {
        LOGGER.error("Det skjedde en ukjent feil: ${exception.message}", exception)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header(HttpHeaders.WARNING, "Det skjedde en ukjent feil: ${exception.message}")
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(JwtTokenUnauthorizedException::class)
    fun handleUnauthorizedException(exception: JwtTokenUnauthorizedException): ResponseEntity<*> {
        LOGGER.warn("Ugyldig eller manglende sikkerhetstoken", exception)
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.WARNING, "Ugyldig eller manglende sikkerhetstoken")
            .build<Any>()
    }
}
