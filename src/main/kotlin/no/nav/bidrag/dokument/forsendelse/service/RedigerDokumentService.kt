package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.DokumentMetadata
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentDetaljer
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRedigeringMetadataResponsDto
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
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
    private val dokumentTjeneste: DokumentTjeneste,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val fysiskDokumentService: FysiskDokumentService
) {

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

        val dokumentMetadataList = hentDokumentMetadata(dokument, forsendelseId)

        return DokumentRedigeringMetadataResponsDto(
            tittel = dokument.tittel,
            redigeringMetadata = dokument.metadata.hentRedigeringmetadata(),
            dokumenter = dokumentMetadataList.map {
                DokumentDetaljer(
                    tittel = it.tittel ?: it.dokumentreferanse ?: "",
                    dokumentreferanse = it.dokumentreferanse,
                    antallSider = 0
                )
            }
        )
    }

    private fun hentDokumentDetaljer(dokument: Dokument, dokumentMetadata: DokumentMetadata) {
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
            .map {
                if (it.dokumentreferanse == dokumentreferanse) {
                    it.copy(
                        metadata = redigeringMetadata.let { rd ->
                            val metadata = it.metadata
                            metadata.lagreRedigeringmetadata(rd)
                            metadata.copy()
                        }
                    )
                } else {
                    it
                }
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }
}
