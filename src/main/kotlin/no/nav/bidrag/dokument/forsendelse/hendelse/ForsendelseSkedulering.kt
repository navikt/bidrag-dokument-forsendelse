package no.nav.bidrag.dokument.forsendelse.hendelse

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.service.DistribusjonService
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import javax.transaction.Transactional

private val LOGGER = KotlinLogging.logger {}

@Component
class ForsendelseSkedulering(
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val distribusjonService: DistribusjonService,
    @Value("\${LAGRE_DIST_INFO_PAGE_SIZE:10}") private val distInfoPageSize: Int,
) {

    @Scheduled(cron = "\${LAGRE_DIST_INFO_CRON}")
    @SchedulerLock(name = "lagreDistribusjonsinfo", lockAtLeastFor = "30m")
    @Transactional
    fun lagreDistribusjonsinfoSkeduler() {
        lagreDistribusjoninfo()
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