package no.nav.bidrag.dokument.forsendelse.hendelse

import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.repository.DokumentRepository
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragDokumentBestillingKonsumer
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentArkivSystemTo
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentBestillingForespørsel
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import no.nav.bidrag.dokument.forsendelse.model.KunneIkkBestilleDokument
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import javax.transaction.Transactional
private val LOGGER = KotlinLogging.logger {}

@Component
class DokumentBestillingLytter(
    val dokumentBestillingKonsumer: BidragDokumentBestillingKonsumer,
    val forsendelseRepository: ForsendelseRepository,
    val dokumentRepository: DokumentRepository
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun bestill(dokumentBestilling: DokumentBestilling) {
        val (forsendelseId, dokumentreferanse) = dokumentBestilling
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId) ?: throw KunneIkkBestilleDokument("Fant ikke forsendelse $forsendelseId")
        val dokument = forsendelse.dokumenter.hent(dokumentreferanse)
            ?: throw KunneIkkBestilleDokument("Fant ikke dokument med dokumentreferanse $dokumentreferanse i forsendelse ${forsendelse.forsendelseId}")
        if (dokument.dokumentmalId.isNullOrEmpty()) throw KunneIkkBestilleDokument("Dokument med dokumentreferanse $dokumentreferanse mangler dokumentmalId")

        val bestilling = tilForespørsel(forsendelse, dokument)

        try {
            val respons = dokumentBestillingKonsumer.bestill(bestilling, dokument.dokumentmalId)

            dokumentRepository.save(
                dokument.copy(
                    arkivsystem = when (respons?.arkivSystem) {
                        DokumentArkivSystemTo.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.BREVSERVER
                        else -> DokumentArkivSystem.UKJENT
                    },
                    dokumentStatus = DokumentStatus.BESTILT
                )
            )
        } catch (e: Exception){
            LOGGER.error(e){ "Det skjedde en feil ved bestilling av dokumentmal ${dokument.dokumentmalId} for dokumentreferanse $dokumentreferanse og forsendelseId $forsendelseId" }
        }

    }

    private fun tilForespørsel(forsendelse: Forsendelse, dokument: Dokument): DokumentBestillingForespørsel =
        DokumentBestillingForespørsel(
            dokumentreferanse = dokument.dokumentreferanse,
            saksnummer = forsendelse.saksnummer,
            tittel = dokument.tittel,
            gjelderId = forsendelse.gjelderIdent,
            mottakerId = forsendelse.mottaker?.ident,
            enhet = forsendelse.enhet,
            språk = forsendelse.språk
        )
}