package no.nav.bidrag.dokument.forsendelse.hendelse

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val LOGGER = KotlinLogging.logger {}

@Component
class DokumentSkedulering(private val dokumentTjeneste: DokumentTjeneste, private val bestillingLytter: DokumentBestillingLytter) {


    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.MINUTES, initialDelay = 10)
    @SchedulerLock(name = "bestillFeiledeDokumenterPaNytt", lockAtLeastFor = "10m")
    fun bestillFeiledeDokumenterPåNyttSkeduler() {
        bestillFeiledeDokumenterPåNytt()
    }

    fun bestillFeiledeDokumenterPåNytt() {

        val dokumenter = dokumentTjeneste.hentDokumenterSomHarStatusBestillingFeilet()
        LOGGER.info { "Fant ${dokumenter.size} dokumenter som har status ${DokumentStatus.BESTILLING_FEILET.name}. Prøver å bestille dokumentene på nytt." }

        SikkerhetsKontekst.medApplikasjonKontekst {
            dokumenter.forEach {
                bestillingLytter.bestill(DokumentBestilling(it.forsendelse.forsendelseId!!, it.dokumentreferanse))
            }
        }
    }
}