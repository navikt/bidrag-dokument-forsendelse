package no.nav.bidrag.dokument.forsendelse.hendelse

import jakarta.transaction.Transactional
import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.dokument.forsendelse.model.BIDRAG_DOKUMENT_FORSENDELSE_APP_ID
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.DistribusjonService
import no.nav.bidrag.dokument.forsendelse.service.ForsendelseHendelseBestillingService
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.transport.dokument.JournalpostStatus
import no.nav.bidrag.transport.dokument.Kanal
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
    @Value("\${LAGRE_DIST_INFO_PAGE_SIZE:10}") private val distInfoPageSize: Int
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
                    ?.takeIf { it.journalstatus == JournalpostStatus.DISTRIBUERT || it.journalstatus == JournalpostStatus.EKSPEDERT || it.kanal == Kanal.INGEN_DISTRIBUSJON.name }
                    ?.let { distInfo ->
                        LOGGER.info {
                            "Forsendelse ${forsendelse.forsendelseId} har status ${ForsendelseStatus.FERDIGSTILT} men journalpost ${forsendelse.journalpostIdFagarkiv} er distribuert med status ${distInfo.journalstatus} og kanal ${distInfo.kanal}. " +
                                "Oppdaterer forsendelsestatus til ${ForsendelseStatus.DISTRIBUERT}"
                        }
                        val kanal = DistribusjonKanal.valueOf(distInfo.kanal)
                        forsendelseTjeneste.lagre(
                            forsendelse.copy(
                                status = when (kanal) {
                                    DistribusjonKanal.LOKAL_UTSKRIFT -> ForsendelseStatus.DISTRIBUERT_LOKALT
                                    DistribusjonKanal.INGEN_DISTRIBUSJON -> ForsendelseStatus.FERDIGSTILT
                                    else -> ForsendelseStatus.DISTRIBUERT
                                },
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

    /**
     * Hvis forsendelse distribuert til NAVNO ikke blir lest etter 40 timer så sendes forsendelse via sentral print istedenfor.
     * Denne jobben skal resynke kanal som forsendelse ble distribuert til
     */
    fun resynkDistribusjoninfoNavNo(
        simulering: Boolean = false,
        latestDate: LocalDateTime? = null,
        earliestDate: LocalDateTime? = null
    ): List<Forsendelse> {
        val forsendelseListe = forsendelseTjeneste.hentDistribuerteForsendelserDistribuertTilNavNo(distInfoPageSize, latestDate, earliestDate)
        LOGGER.info { "Fant ${forsendelseListe.size} forsendelser som har blitt distribuert til NAV_NO. Sjekker distribusjon kanal på nytt for forsendelsene for å se om de har blitt redistribuert til sentral print. lesStørrelse=$distInfoPageSize" }
        return forsendelseListe.mapNotNull {
            lagreDistribusjonInfo(it, simulering)
        }.filter { simulering || it.distribusjonKanal == DistribusjonKanal.SENTRAL_UTSKRIFT }
    }

    fun lagreDistribusjoninfo() {
        val forsendelseListe = forsendelseTjeneste.hentDistribuerteForsendelserUtenDistribusjonKanal(distInfoPageSize)
        LOGGER.info { "Fant ${forsendelseListe.size} forsendelser som har blitt distribuert men mangler distribusjon kanal. Henter og lagrer distribusjon kanal for forsendelsene. lesStørrelse=$distInfoPageSize" }
        forsendelseListe.forEach {
            lagreDistribusjonInfo(it)
        }
    }

    private fun lagreDistribusjonInfo(forsendelse: Forsendelse, simulering: Boolean = false): Forsendelse? {
        return try {
            if (!forsendelse.journalpostIdFagarkiv.isNullOrEmpty()) {
                distribusjonService.hentDistribusjonInfo(forsendelse.journalpostIdFagarkiv)
                    ?.takeIf { forsendelse.distribusjonKanal == null || DistribusjonKanal.valueOf(it.kanal) != forsendelse.distribusjonKanal }
                    ?.let { distInfo ->
                        LOGGER.info {
                            "Lagrer forsendelse distribusjon info for forsendelse ${forsendelse.forsendelseId}" +
                                "med JOARK journalpostId ${forsendelse.journalpostIdFagarkiv}, bestillingId=${distInfo.bestillingId}, " +
                                "${forsendelse.dokumenter.size} dokumenter, " +
                                "kanal ${distInfo.kanal} og status ${distInfo.journalstatus}. Forsendelsens kanal var ${forsendelse.distribusjonKanal}. Er simulering=$simulering"
                        }
                        return if (simulering) {
                            forsendelse
                        } else {
                            forsendelseTjeneste.lagre(
                                forsendelse.copy(
                                    bestiltNyDistribusjon = forsendelse.distribusjonKanal != null,
                                    distribusjonBestillingsId = distInfo.bestillingId ?: forsendelse.distribusjonBestillingsId,
                                    distribusjonKanal = DistribusjonKanal.valueOf(distInfo.kanal)
                                )
                            )
                        }
                    }
            } else {
                null
            }
        } catch (e: Exception) {
            LOGGER.error(e) { "Det skjedde en feil ved lagring av forsendelse distribusjonsinfo for forsendelse ${forsendelse.forsendelseId}" }
            return null
        }
    }
}
