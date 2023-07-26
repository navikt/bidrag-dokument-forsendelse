package no.nav.bidrag.dokument.forsendelse.hendelse

import jakarta.transaction.Transactional
import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestillSletting
import no.nav.bidrag.dokument.forsendelse.model.KunneIkkBestilleDokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.service.DokumentStorageService
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val LOGGER = KotlinLogging.logger {}

@Component
class DokumentSlettingLytter(
    val dokumentStorageService: DokumentStorageService,
    val forsendelseRepository: ForsendelseRepository,
    val dokumentTjeneste: DokumentTjeneste
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun slettDokument(dokumentBestilling: DokumentBestillSletting) {
        val (forsendelseId, dokumentreferanse) = dokumentBestilling
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId)
            ?: throw KunneIkkBestilleDokument("Fant ikke forsendelse $forsendelseId")
        val dokument = forsendelse.dokumenter.hentDokument(dokumentreferanse)
            ?: throw KunneIkkBestilleDokument("Fant ikke dokument med dokumentreferanse $dokumentreferanse i forsendelse ${forsendelse.forsendelseId}")

        try {
            val gcpFilsti = dokument.metadata.hentGcpFilsti() ?: dokument.filsti
            dokumentStorageService.slettFil(dokument.filsti)
            dokumentTjeneste.lagreDokument(
                dokument.copy(
                    metadata = run {
                        val metadata = dokument.metadata
                        metadata.lagreGcpFilsti(null)
                        metadata.lagreGcpKrypteringnøkkelVersjon(null)
                        metadata.copy()
                    },
                )
            )
            LOGGER.info { "Slettet fil med GCP filsti $gcpFilsti som tilhører dokument ${dokument.dokumentreferanse} og forsendelse ${forsendelse.forsendelseId}. Forsendelse har status ${forsendelse.status}" }
        } catch (e: Exception) {
            LOGGER.error(e) { "Kunne ikke slettet fil med filsti ${dokument.filsti} som tilhører dokument ${dokument.dokumentreferanse} og forsendelse ${forsendelse.forsendelseId}" }
        }

    }
}