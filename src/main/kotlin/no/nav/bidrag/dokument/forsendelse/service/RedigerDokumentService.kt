package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DokumentMetadata
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentDetaljer
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRedigeringMetadataResponsDto
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.FerdigstillDokumentRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.mapper.tilDokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseStatusTo
import no.nav.bidrag.dokument.forsendelse.model.bytesIntoHumanReadable
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.DokumentMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.pdf.PDFDokumentDetails
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanEndreForsendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import javax.transaction.Transactional

private val log = KotlinLogging.logger {}

@Component
@Transactional
class RedigerDokumentService(
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val dokumentStorageService: DokumentStorageService,
    private val dokumenttjeneste: DokumentTjeneste,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val fysiskDokumentService: FysiskDokumentService
) {

    fun opphevFerdigstillDokument(forsendelseId: Long, dokumentreferanse: String): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        log.info {
            "Opphever ferdigstilling av dokument $dokumentreferanse i forsendelse $forsendelseId"
        }
        val oppdatertForsendelse = forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = opphevFerdigstillDokument(forsendelse, dokumentreferanse),
                endretTidspunkt = LocalDateTime.now(),
                endretAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId() ?: forsendelse.endretAvIdent
            )
        )

        val oppdatertDokument = oppdatertForsendelse.dokumenter.hentDokument(dokumentreferanse)!!

        log.info { "Opphevet ferdigstilling av dokument $dokumentreferanse i forsendelse $forsendelseId" }
        return DokumentRespons(
            dokumentreferanse = oppdatertDokument.dokumentreferanse,
            tittel = oppdatertDokument.tittel,
            dokumentDato = oppdatertDokument.dokumentDato,
            status = oppdatertDokument.tilDokumentStatusTo()
        )
    }

    fun ferdigstillDokument(forsendelseId: Long, dokumentreferanse: String, ferdigstillDokumentRequest: FerdigstillDokumentRequest): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        log.info {
            "Ferdigstiller dokument $dokumentreferanse i forsendelse $forsendelseId med dokumentstørrelse ${
                bytesIntoHumanReadable(
                    ferdigstillDokumentRequest.fysiskDokument.size.toLong()
                )
            }"
        }
        val oppdatertForsendelse = forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = ferdigstillDokument(forsendelse, dokumentreferanse, ferdigstillDokumentRequest),
                endretTidspunkt = LocalDateTime.now(),
                endretAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId() ?: forsendelse.endretAvIdent
            )
        )

        val oppdatertDokument = oppdatertForsendelse.dokumenter.hentDokument(dokumentreferanse)!!

        log.info { "Ferdigstilt dokument $dokumentreferanse i forsendelse $forsendelseId" }
        return DokumentRespons(
            dokumentreferanse = oppdatertDokument.dokumentreferanse,
            tittel = oppdatertDokument.tittel,
            dokumentDato = oppdatertDokument.dokumentDato,
            status = oppdatertDokument.tilDokumentStatusTo()
        )
    }

    private fun opphevFerdigstillDokument(
        forsendelse: Forsendelse,
        dokumentreferanse: String
    ): List<Dokument> {
        val oppdaterteDokumenter = forsendelse.dokumenter
            .map {
                (it.dokumentreferanse == dokumentreferanse).ifTrue {
                    it.copy(
                        dokumentStatus = DokumentStatus.MÅ_KONTROLLERES
                    )
                } ?: it
            }

        val dokument = oppdaterteDokumenter.hentDokument(dokumentreferanse)!!
        dokumentStorageService.slettFil(dokument.filsti)

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    private fun ferdigstillDokument(
        forsendelse: Forsendelse,
        dokumentreferanse: String,
        ferdigstillDokumentRequest: FerdigstillDokumentRequest
    ): List<Dokument> {
        val (fysiskDokument, redigeringMetadata) = ferdigstillDokumentRequest
        val oppdaterteDokumenter = forsendelse.dokumenter
            .map {
                (it.dokumentreferanse == dokumentreferanse).ifTrue {
                    it.copy(
                        metadata = redigeringMetadata?.let { rd -> oppdaterDokumentRedigeringMetadata(it, rd) }
                            ?: it.metadata,
                        dokumentStatus = when (it.dokumentStatus) {
                            DokumentStatus.MÅ_KONTROLLERES, DokumentStatus.KONTROLLERT -> DokumentStatus.KONTROLLERT
                            DokumentStatus.UNDER_REDIGERING, DokumentStatus.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
                            else -> it.dokumentStatus
                        }
                    )
                } ?: it
            }

        val dokument = oppdaterteDokumenter.hentDokument(dokumentreferanse)!!
        dokumentStorageService.lagreFil(dokument.filsti, fysiskDokument)

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }


    fun oppdaterDokumentRedigeringMetadata(forsendelseId: Long, dokumentreferanse: String, redigeringMetadata: String) {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        log.info { "Oppdaterer dokument redigeringmetadata for dokument $dokumentreferanse i forsendelse $forsendelseId" }
        forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = oppdaterDokumentRedigeringMetadata(forsendelse, dokumentreferanse, redigeringMetadata),
                endretTidspunkt = LocalDateTime.now(),
                endretAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId() ?: forsendelse.endretAvIdent
            )
        )
    }

    fun hentDokumentredigeringMetadata(
        forsendelseId: Long,
        dokumentreferanse: String
    ): DokumentRedigeringMetadataResponsDto {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)

        val dokument = forsendelse.dokumenter.hentDokument(dokumentreferanse) ?: fantIkkeDokument(forsendelseId, dokumentreferanse)

        return DokumentRedigeringMetadataResponsDto(
            tittel = dokument.tittel,
            forsendelseStatus = forsendelse.tilForsendelseStatusTo(),
            status = dokument.tilDokumentStatusTo(),
            redigeringMetadata = dokument.metadata.hentRedigeringmetadata(),
            dokumenter = hentAlleDokumentDetaljer(dokument, forsendelseId)
        )
    }

    private fun hentAlleDokumentDetaljer(dokument: Dokument, forsendelseId: Long): List<DokumentDetaljer> {
        val existing = dokument.metadata.hentDokumentDetaljer()
        if (existing != null) return existing

        val dokumentMetadataList = hentDokumentMetadata(dokument, forsendelseId)
        val dokumentDetaljer = dokumentMetadataList.map { hentDokumentDetaljer(it) }
        val metadata = dokument.metadata
        metadata.lagreDokumentDetaljer(dokumentDetaljer)
        dokumenttjeneste.lagreDokument(
            dokument.copy(
                metadata = metadata.copy()
            )
        )
        return dokumentDetaljer
    }

    private fun hentDokumentDetaljer(dokumentMetadata: DokumentMetadata): DokumentDetaljer {
        val dokumentFil = fysiskDokumentService.hentFysiskDokument(dokumentMetadata)
        val numerOfPages = PDFDokumentDetails().getNumberOfPages(dokumentFil)
        return DokumentDetaljer(
            tittel = dokumentMetadata.tittel ?: dokumentMetadata.dokumentreferanse ?: "",
            dokumentreferanse = dokumentMetadata.dokumentreferanse,
            antallSider = numerOfPages
        )
    }

    private fun hentDokumentMetadata(dokument: Dokument, forsendelseId: Long): List<DokumentMetadata> {
        return if (dokument.erFraAnnenKilde) {
            bidragDokumentConsumer.hentDokumentMetadata(
                dokument.journalpostId!!,
                dokument.dokumentreferanseOriginal
            )
        } else {
            fysiskDokumentService.hentDokumentMetadata(forsendelseId, dokumentreferanse = dokument.dokumentreferanse)
        }
    }

    private fun oppdaterDokumentRedigeringMetadata(
        forsendelse: Forsendelse,
        dokumentreferanse: String,
        redigeringMetadata: String
    ): List<Dokument> {
        val oppdaterteDokumenter = forsendelse.dokumenter
            .map { dokument ->
                (dokument.dokumentreferanse == dokumentreferanse).ifTrue {
                    dokument.copy(
                        metadata = oppdaterDokumentRedigeringMetadata(dokument, redigeringMetadata)
                    )
                } ?: dokument
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    private fun oppdaterDokumentRedigeringMetadata(dokument: Dokument, redigeringMetadata: String): DokumentMetadataDo {
        return redigeringMetadata.let { rd ->
            val metadata = dokument.metadata
            metadata.lagreRedigeringmetadata(rd)
            metadata.copy()
        }

    }
}
