package no.nav.bidrag.dokument.forsendelse.aop

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.commons.CorrelationId
import no.nav.bidrag.commons.CorrelationId.Companion.CORRELATION_ID_HEADER
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

private val LOGGER = KotlinLogging.logger {}

@Component
@Aspect
@Suppress("ktlint:standard:max-line-length")
class HendelseCorrelationAspect(
    private val objectMapper: ObjectMapper,
) {
    @Before(
        value = "execution(* no.nav.bidrag.dokument.forsendelse.hendelse.DokumentHendelseLytter.prossesserDokumentHendelse(..)) && args(hendelse)",
    )
    fun leggSporingFraDokumentHendelseTilMDC(
        joinPoint: JoinPoint,
        hendelse: ConsumerRecord<String, String>,
    ) {
        hentSporingFraHendelse(hendelse)?.let {
            val correlationId = CorrelationId.existing(it)
            MDC.put(CORRELATION_ID_HEADER, correlationId.get())
        } ?: run {
            val tilfeldigVerdi = UUID.randomUUID().toString().subSequence(0, 8)
            val korrelasjonsId = "${tilfeldigVerdi}_prossesserDokumentHendelse"
            MDC.put(CORRELATION_ID_HEADER, CorrelationId.existing(korrelasjonsId).get())
        }
    }

    @Before(value = "execution(* no.nav.bidrag.dokument.forsendelse.hendelse.*.*(..))")
    fun leggtilHendelseSporingId(joinPoint: JoinPoint) {
        if (MDC.get(CORRELATION_ID_HEADER) != null) return
        val tilfeldigVerdi = UUID.randomUUID().toString().subSequence(0, 8)
        val methodName = joinPoint.signature.name.replace("[^A-Za-z0-9 ]".toRegex(), "") // Fjern norske bokstaver fra metodenavn
        val korrelasjonsId = "${tilfeldigVerdi}_$methodName"
        MDC.put(CORRELATION_ID_HEADER, CorrelationId.existing(korrelasjonsId).get())
    }

    private fun hentSporingFraHendelse(hendelse: ConsumerRecord<String, String>): String? =
        try {
            val jsonNode = objectMapper.readTree(hendelse.value())
            jsonNode["sporingId"].asText()
        } catch (e: Exception) {
            LOGGER.error("Det skjedde en feil ved konverting av melding fra hendelse", e)
            null
        }

    @After(value = "execution(* no.nav.bidrag.dokument.forsendelse.hendelse.*.*(..))")
    fun clearCorrelationId(joinPoint: JoinPoint) {
        MDC.clear()
    }
}
