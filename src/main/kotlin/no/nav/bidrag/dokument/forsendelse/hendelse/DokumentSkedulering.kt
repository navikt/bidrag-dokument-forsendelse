package no.nav.bidrag.dokument.forsendelse.hendelse

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private val LOGGER = KotlinLogging.logger {}

@Component
class DokumentSkedulering(private val dokumentTjeneste: DokumentTjeneste, private val bestillingLytter: DokumentBestillingLytter) {

    @Scheduled(fixedDelay = 15, timeUnit = TimeUnit.MINUTES, initialDelay = 10)
    @Scheduled(cron = "\${REBESTILL_DOKUMENTER_BESTILLING_FEILET_SCHEDULE}")
    @SchedulerLock(name = "bestillFeiledeDokumenterPaNytt", lockAtLeastFor = "10m")
    fun bestillFeiledeDokumenterPåNyttSkeduler() {
        bestillFeiledeDokumenterPåNytt()
    }

    @Scheduled(cron = "\${REBESTILL_DOKUMENTER_UNDER_PRODUKSJON_SCHEDULE}")
    @SchedulerLock(name = "bestilleDokumenterUnderProduksjonPaNyttSkeduler", lockAtLeastFor = "10m")
    fun bestilleDokumenterUnderProduksjonPåNyttSkeduler() {
        bestillDokumenterUnderProduksjonPåNytt()
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

    fun bestillDokumenterUnderProduksjonPåNytt() {
        val threshold = LocalDateTime.now().minusMinutes(10)
        val dokumenter = dokumentTjeneste.hentDokumenterSomHarStatusUnderProduksjon().filter {
            val bestiltTidspunkt = it.metadata.hentBestiltTidspunkt()
            val bestiltFørTerskel = bestiltTidspunkt != null && bestiltTidspunkt <= threshold
            it.metadata.hentDokumentBestiltAntallGanger() < 10 && bestiltFørTerskel
        }
        LOGGER.info { "Fant ${dokumenter.size} dokumenter som har status ${DokumentStatus.UNDER_PRODUKSJON.name} som er eldre enn $threshold. Prøver å bestille dokumentene på nytt." }

        SikkerhetsKontekst.medApplikasjonKontekst {
            dokumenter.forEach {
                bestillingLytter.bestill(DokumentBestilling(it.forsendelse.forsendelseId!!, it.dokumentreferanse))
            }
        }
    }
}
