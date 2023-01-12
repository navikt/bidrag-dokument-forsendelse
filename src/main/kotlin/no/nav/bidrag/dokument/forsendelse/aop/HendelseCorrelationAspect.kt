package no.nav.bidrag.dokument.forsendelse.aop

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import no.nav.bidrag.commons.CorrelationId
import no.nav.bidrag.commons.web.interceptor.MdcValuesPropagatingClientInterceptor.Companion.CORRELATION_ID
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
class HendelseCorrelationAspect(private val objectMapper: ObjectMapper) {

    @Before(value = "execution(* no.nav.bidrag.dokument.forsendelse.hendelse.DokumentHendelseLytter.prossesserDokumentHendelse(..)) && args(hendelse)")
    fun leggSporingFraDokumentHendelseTilMDC(joinPoint: JoinPoint, hendelse: ConsumerRecord<String, String>) {
        hentSporingFraHendelse(hendelse)?.let {
            val correlationId = CorrelationId.existing(it)
            MDC.put(CORRELATION_ID, correlationId.get())
        } ?: run {
            val tilfeldigVerdi = UUID.randomUUID().toString().subSequence(0, 8)
            val korrelasjonsId = "${tilfeldigVerdi}_prossesserDokumentHendelse"
            MDC.put(CORRELATION_ID, CorrelationId.existing(korrelasjonsId).get())
        }

    }

    private fun hentSporingFraHendelse(hendelse: ConsumerRecord<String, String>): String? {
        return try {
            val jsonNode = objectMapper.readTree(hendelse.value())
            jsonNode["sporingId"].asText()
        } catch (e: Exception){
            LOGGER.error("Det skjedde en feil ved konverting av melding fra hendelse", e)
            null
        }
    }
    @After(value = "execution(* no.nav.bidrag.dokument.forsendelse.hendelse.DokumentHendelseLytter.*(..))")
    fun clearCorrelationIdFromScheduler(joinPoint: JoinPoint) {
        MDC.clear()
    }
}
