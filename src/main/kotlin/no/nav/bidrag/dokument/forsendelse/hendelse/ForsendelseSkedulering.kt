package no.nav.bidrag.dokument.forsendelse.hendelse

import jakarta.transaction.Transactional
import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.dokument.dto.JournalpostStatus
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.model.BIDRAG_DOKUMENT_FORSENDELSE_APP_ID
import no.nav.bidrag.dokument.forsendelse.service.DistribusjonService
import no.nav.bidrag.dokument.forsendelse.service.ForsendelseHendelseBestillingService
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Component
class ForsendelseSkedulering(
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val distribusjonService: DistribusjonService,
    private val forsendelseHendelseBestilling: ForsendelseHendelseBestillingService,
    @Value("\${LAGRE_DIST_INFO_PAGE_SIZE:10}") private val distInfoPageSize: Int,
    @Value("\${OPPDATER_DIST_STATUS_ENABLED:true}") private val forsendelseDistStatusEnabled: Boolean
) {

    @Scheduled(cron = "\${LAGRE_DIST_INFO_CRON}")
    @SchedulerLock(name = "lagreDistribusjonsinfo", lockAtLeastFor = "10m")
    @Transactional
    fun lagreDistribusjonsinfoSkeduler() {
        lagreDistribusjoninfo()
    }

    @Scheduled(cron = "\${OPPDATER_DIST_STATUS_CRON}")
    @SchedulerLock(name = "oppdaterDistribusjonstatus", lockAtLeastFor = "10m")
    @Transactional
    fun oppdaterDistribusjonstatusSkeduler() {
        oppdaterDistribusjonstatus()
    }

    fun oppdaterDistribusjonstatus() {
        val forsendelseListe = forsendelseTjeneste.hentFerdigstilteIkkeDistribuert()
        LOGGER.info { "Fant ${forsendelseListe.size} utgående forsendelser som har status FERDIGSTILT. Sjekker om journalposten har blitt distribuert" }
        forsendelseListe.forEach {
            oppdaterForsendelsestatusTilDistribuert(it)
        }
    }

    private fun oppdaterForsendelsestatusTilDistribuert(forsendelse: Forsendelse) {
        try {
            if (!forsendelse.journalpostIdFagarkiv.isNullOrEmpty()) {
                distribusjonService.hentDistribusjonInfo(forsendelse.journalpostIdFagarkiv)
                    ?.takeIf { it.journalstatus == JournalpostStatus.DISTRIBUERT || it.journalstatus == JournalpostStatus.EKSPEDERT }
                    ?.let { distInfo ->
                        LOGGER.info {
                            "Forsendelse ${forsendelse.forsendelseId} har status ${ForsendelseStatus.FERDIGSTILT} men journalpost ${forsendelse.journalpostIdFagarkiv} er distribuert med status ${distInfo.journalstatus} og kanal ${distInfo.kanal}. " +
                                "Oppdaterer forsendelsestatus til ${ForsendelseStatus.DISTRIBUERT}"
                        }
                        if (!forsendelseDistStatusEnabled) {
                            LOGGER.info {
                                "Oppdatering av Forsendelse status er ikke skrudd på. Oppdaterer ikke forsendelse"
                            }
                            return
                        }
                        val kanal = DistribusjonKanal.valueOf(distInfo.kanal)
                        forsendelseTjeneste.lagre(
                            forsendelse.copy(
                                status = if (kanal == DistribusjonKanal.LOKAL_UTSKRIFT) ForsendelseStatus.DISTRIBUERT_LOKALT else ForsendelseStatus.DISTRIBUERT,
                                distribuertTidspunkt = distInfo.distribuertDato ?: LocalDateTime.now(),
                                distribuertAvIdent = distInfo.distribuertAvIdent ?: forsendelse.distribuertAvIdent,
                                distribusjonBestillingsId = distInfo.bestillingId ?: forsendelse.distribusjonBestillingsId,
                                distribusjonKanal = kanal,
                                endretTidspunkt = LocalDateTime.now(),
                                endretAvIdent = BIDRAG_DOKUMENT_FORSENDELSE_APP_ID
                            )
                        )
                        forsendelseHendelseBestilling.bestill(forsendelse.forsendelseId!!)
                    }
            }
        } catch (e: Exception) {
            LOGGER.error(e) { "Det skjedde en feil ved lagring av forsendelse distribusjonstatus for forsendelse ${forsendelse.forsendelseId}" }
        }
    }

    fun lagreDistribusjoninfo() {
        val forsendelseListe = forsendelseTjeneste.hentDistribuerteForsendelserUtenDistribusjonKanal(distInfoPageSize)
        LOGGER.info { "Fant ${forsendelseListe.size} forsendelser som har blitt distribuert men mangler distribusjon kanal. Henter og lagrer distribusjon kanal for forsendelsene. lesStørrelse=$distInfoPageSize" }
        forsendelseListe.forEach {
            lagreDistribusjonInfo(it)
        }
    }

    private fun lagreDistribusjonInfo(forsendelse: Forsendelse) {
        try {
            if (!forsendelse.journalpostIdFagarkiv.isNullOrEmpty()) {
                distribusjonService.hentDistribusjonInfo(forsendelse.journalpostIdFagarkiv)?.let { distInfo ->
                    LOGGER.info {
                        "Lagrer forsendelse distribusjon info for forsendelse ${forsendelse.forsendelseId} " +
                            "med JOARK journalpostId ${forsendelse.journalpostIdFagarkiv}, " +
                            "${forsendelse.dokumenter.size} dokumenter, " +
                            "kanal ${distInfo.kanal} og status ${distInfo.journalstatus}"
                    }
                    forsendelseTjeneste.lagre(
                        forsendelse.copy(
                            distribusjonKanal = DistribusjonKanal.valueOf(distInfo.kanal)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            LOGGER.error(e) { "Det skjedde en feil ved lagring av forsendelse distribusjonsinfo for forsendelse ${forsendelse.forsendelseId}" }
        }
    }
}
