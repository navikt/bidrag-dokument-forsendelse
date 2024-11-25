package no.nav.bidrag.dokument.forsendelse.hendelse

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Component
class DokumentSkedulering(
    private val dokumentTjeneste: DokumentTjeneste,
    private val bestillingLytter: DokumentBestillingLytter,
    private val dokumentConsumer: BidragDokumentConsumer,
) {
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
        val threshold = LocalDateTime.now().minusMinutes(10)
        val dokumenter =
            dokumentTjeneste.hentDokumenterSomHarStatusBestillingFeilet().filter {
                val bestiltTidspunkt = it.metadata.hentBestiltTidspunkt()
                val bestiltFørTerskel = bestiltTidspunkt != null && bestiltTidspunkt <= threshold
                it.metadata.hentDokumentBestiltAntallGanger() < 20 && bestiltFørTerskel
            }
        LOGGER.info {
            "Fant ${dokumenter.size} dokumenter som har status ${DokumentStatus.BESTILLING_FEILET.name}. " +
                "Prøver å bestille dokumentene på nytt."
        }
        bestill(dokumenter)
    }

    fun bestillDokumenterUnderProduksjonPåNytt() {
        val threshold = LocalDateTime.now().minusMinutes(10)
        val dokumenter =
            dokumentTjeneste.hentDokumenterSomHarStatusUnderProduksjon().filter {
                val bestiltTidspunkt = it.metadata.hentBestiltTidspunkt()
                val bestiltFørTerskel = bestiltTidspunkt != null && bestiltTidspunkt <= threshold
                it.metadata.hentDokumentBestiltAntallGanger() < 10 && bestiltFørTerskel
            }
        LOGGER.info {
            "Fant ${dokumenter.size} dokumenter som har status ${DokumentStatus.UNDER_PRODUKSJON.name} som er eldre enn $threshold. " +
                "Prøver å bestille dokumentene på nytt."
        }
        bestill(dokumenter)
    }

    fun bestill(dokumenter: List<Dokument>) {
        SikkerhetsKontekst.medApplikasjonKontekst {
            dokumenter.forEach {
                LOGGER.info {
                    "Bestiller dokument med mal ${it.dokumentmalId} og tittel ${it.tittel} for dokumentreferanse ${it.dokumentreferanse}." +
                        " Dokumentet ble sist bestilt ${it.metadata.hentBestiltTidspunkt()} " +
                        "og bestilt totalt ${it.metadata.hentDokumentBestiltAntallGanger()} ganger"
                }
                bestillingLytter.bestill(DokumentBestilling(it.forsendelse.forsendelseId!!, it.dokumentreferanse))
            }
        }
    }
}
