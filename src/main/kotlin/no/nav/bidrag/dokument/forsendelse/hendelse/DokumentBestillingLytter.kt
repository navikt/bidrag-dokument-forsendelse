package no.nav.bidrag.dokument.forsendelse.hendelse

import mu.KotlinLogging
import no.nav.bidrag.commons.CorrelationId
import no.nav.bidrag.dokument.dto.DokumentHendelse
import no.nav.bidrag.dokument.dto.DokumentHendelseType
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragDokumentBestillingKonsumer
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentArkivSystemTo
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentBestillingForespørsel
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import no.nav.bidrag.dokument.forsendelse.model.KunneIkkBestilleDokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.DokumentTjeneste
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
    val dokumentTjeneste: DokumentTjeneste,
    val dokumentKafkaHendelseProdusent: DokumentKafkaHendelseProdusent
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

            dokumentTjeneste.lagreDokument(
                dokument.copy(
                    arkivsystem = when (respons?.arkivSystem) {
                        DokumentArkivSystemTo.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                        else -> DokumentArkivSystem.UKJENT
                    },
                    dokumentStatus = DokumentStatus.UNDER_PRODUKSJON
                )
            )
        } catch (e: Exception){
            dokumentTjeneste.lagreDokument(
                dokument.copy(dokumentStatus = DokumentStatus.BESTILLING_FEILET)
            )
            LOGGER.error(e){ "Det skjedde en feil ved bestilling av dokumentmal ${dokument.dokumentmalId} for dokumentreferanse $dokumentreferanse og forsendelseId $forsendelseId" }
        }

    }

    private fun tilForespørsel(forsendelse: Forsendelse, dokument: Dokument): DokumentBestillingForespørsel =
        DokumentBestillingForespørsel(
            dokumentreferanse = dokument.dokumentreferanse,
            saksnummer = forsendelse.saksnummer,
            tittel = dokument.tittel,
            gjelderId = forsendelse.gjelderIdent,
            enhet = forsendelse.enhet,
            språk = forsendelse.språk,
            mottaker = forsendelse.mottaker?.let { mottaker ->
                MottakerTo(mottaker.ident, mottaker.navn, mottaker.språk, adresse =mottaker.adresse?.let {
                    MottakerAdresseTo(
                        adresselinje1 = it.adresselinje1,
                        adresselinje2 = it.adresselinje2,
                        adresselinje3 = it.adresselinje3,
                        bruksenhetsnummer = it.bruksenhetsnummer,
                        postnummer = it.postnummer,
                        landkode = it.landkode,
                        landkode3 = it.landkode3,
                        poststed = it.poststed
                    )
                })
            }
        )

    private fun tilBestillingHendelse(forsendelse: Forsendelse, dokument: Dokument): DokumentHendelse =
        DokumentHendelse(
            dokumentreferanse = dokument.dokumentreferanse,
            forsendelseId = "BIF-${forsendelse.forsendelseId}",
            sporingId = CorrelationId.fetchCorrelationIdForThread() ?: CorrelationId.existing("bestilling-${dokument.dokumentreferanse}").get(),
            hendelseType = DokumentHendelseType.BESTILLING,
        )
}