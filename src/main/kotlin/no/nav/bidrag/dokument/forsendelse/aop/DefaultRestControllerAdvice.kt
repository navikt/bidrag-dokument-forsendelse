package no.nav.bidrag.dokument.forsendelse.aop

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.Locale
import javax.servlet.http.HttpServletRequest
@Order(Ordered.HIGHEST_PRECEDENCE)

@RestControllerAdvice
class DefaultRestControllerAdvice {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultRestControllerAdvice::class.java)
    }
    @ResponseBody
    @ExceptionHandler(value = [IllegalArgumentException::class, MethodArgumentTypeMismatchException::class, ConversionFailedException::class, HttpMessageNotReadableException::class])
    fun handleInvalidValueExceptions(exception: Exception): ResponseEntity<*> {
        val cause = exception.cause
        val valideringsFeil = if (cause is MissingKotlinParameterException) createMissingKotlinParameterViolation(cause) else null
        LOGGER.warn("Forespørselen inneholder ugyldig verdi: ${valideringsFeil ?: "ukjent feil"}", exception)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(HttpHeaders.WARNING, "Forespørselen inneholder ugyldig verdi: ${valideringsFeil?:exception.message}")
            .build<Any>()
    }

    private fun createMissingKotlinParameterViolation(ex: MissingKotlinParameterException): String {
        val errorFieldRegex = Regex("\\.([^.]*)\\[\\\"(.*)\"\\]\$")
        val errorMatch = errorFieldRegex.find(ex.path[0].description)!!
        val (objectName, field) = errorMatch.destructured
        return "$objectName.$field kan ikke være null"
    }

    @ResponseBody
    @ExceptionHandler(UgyldigForespørsel::class)
    fun ugyldigForespørsel(exception: UgyldigForespørsel): ResponseEntity<*> {
        LOGGER.warn("Forespørselen inneholder ugyldig data", exception)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header(HttpHeaders.WARNING, "Forespørselen inneholder ugyldig data: ${exception.message}")
            .build<Any>()
    }


    @ResponseBody
    @ExceptionHandler(Exception::class)
    fun handleOtherExceptions(exception: Exception): ResponseEntity<*> {
        LOGGER.warn("Det skjedde en ukjent feil", exception)
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