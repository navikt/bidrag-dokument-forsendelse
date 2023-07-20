package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseIkkeDistribuertResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.mapper.DokumentDtoMetadata
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.mapper.tilJournalpostDto
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.forsendelseHarIngenBehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

val List<Forsendelse>.filtrerIkkeFerdigstiltEllerArkivert
    get() = this.filter { it.journalpostIdFagarkiv == null }.filter { it.status != ForsendelseStatus.SLETTET }

@Component
class ForsendelseInnsynTjeneste(
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val tilgangskontrollService: TilgangskontrollService,
    private val dokumentValgService: DokumentValgService,
    private val dokumentTjeneste: DokumentTjeneste,
    private val forsendelseTittelService: ForsendelseTittelService
) {

    fun hentForsendelserIkkeDistribuert(): List<ForsendelseIkkeDistribuertResponsTo> {
        val journalpostDtoer = forsendelseTjeneste.hentForsendelserOpprettetFørDagensDatoIkkeDistribuert()
            .filter { tilgangskontrollService.harTilgangTilTema(it.tema.name) }
            .map {
                ForsendelseIkkeDistribuertResponsTo(
                    enhet = it.enhet,
                    forsendelseId = it.forsendelseIdMedPrefix,
                    saksnummer = it.saksnummer,
                    opprettetDato = it.opprettetTidspunkt,
                    tittel = it.dokumenter.hoveddokument?.tittel
                )
            }
        log.info { "Hentet ${journalpostDtoer.size} utgående forsendelser som ikke er distribuert" }
        return journalpostDtoer
    }

    fun hentForsendelseForSakJournal(saksnummer: String, temaListe: List<JournalTema> = listOf(JournalTema.BID)): List<JournalpostDto> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)
        val forsendelserFiltrert = forsendelser.filtrerIkkeFerdigstiltEllerArkivert
            .filter { temaListe.map { jt -> jt.name }.contains(it.tema.name) }
            .filter { tilgangskontrollService.harTilgangTilTema(it.tema.name) }
            .map { tilJournalpostDto(it) }

        log.info { "Hentet ${forsendelserFiltrert.size} forsendelser for sak $saksnummer og temaer $temaListe" }
        return forsendelserFiltrert
    }

    private fun tilJournalpostDto(forsendelse: Forsendelse): JournalpostDto {
        val dokumenterMetadata = tilDokumenterMetadata(forsendelse.dokumenter)
        val journalpost = forsendelse.tilJournalpostDto(dokumenterMetadata)
//        if (journalpost.innhold.isNullOrEmpty()) {
//            journalpost.innhold = forsendelseTittelService.opprettForsendelseTittel(forsendelse)
//        }

        return journalpost
    }

    private fun tilForsendelseRespons(forsendelse: Forsendelse): ForsendelseResponsTo {
        val forsendelseRespons = forsendelse.tilForsendelseRespons(tilDokumenterMetadata(forsendelse.dokumenter))
//        if (forsendelseRespons.tittel.isNullOrEmpty()) {
//            return forsendelseRespons.copy(
//                tittel = forsendelseTittelService.opprettForsendelseTittel(forsendelse)
//            )
//        }
        return forsendelseRespons
    }

    private fun tilDokumenterMetadata(dokumenter: List<Dokument>): Map<String, DokumentDtoMetadata> {
        return dokumenter.associate {
            it.dokumentreferanse to run {
                val metadata = DokumentDtoMetadata()
                if (it.erLenkeTilEnAnnenForsendelse) {
                    val originalDokument = dokumentTjeneste.hentOriginalDokument(it)
                    metadata.oppdaterOriginalDokumentreferanse(originalDokument.dokumentreferanseOriginal)
                    metadata.oppdaterOriginalJournalpostId(originalDokument.journalpostIdOriginal)
                } else {
                    metadata.oppdaterOriginalDokumentreferanse(it.dokumentreferanseOriginal)
                    metadata.oppdaterOriginalJournalpostId(it.journalpostIdOriginal)
                }
                metadata
            }
        }
    }

    fun hentForsendelseJournal(forsendelseId: Long, saksnummer: String? = null): JournalpostResponse {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)

        if (!saksnummer.isNullOrEmpty() && saksnummer != forsendelse.saksnummer) fantIkkeForsendelse(forsendelseId, saksnummer)

        log.debug { "Hentet forsendelse $forsendelseId med saksnummer ${forsendelse.saksnummer}" }

        return JournalpostResponse(
            journalpost = tilJournalpostDto(forsendelse),
            sakstilknytninger = listOf(forsendelse.saksnummer)
        )
    }

    fun hentForsendelseForSak(saksnummer: String): List<ForsendelseResponsTo> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.filtrerIkkeFerdigstiltEllerArkivert
            .map { tilForsendelseRespons(it) }
    }

    fun hentForsendelse(forsendelseId: Long, saksnummer: String? = null): ForsendelseResponsTo {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        if (!saksnummer.isNullOrEmpty() && saksnummer != forsendelse.saksnummer) fantIkkeForsendelse(forsendelseId, saksnummer)

        return tilForsendelseRespons(forsendelse)
    }

    fun hentDokumentvalgForsendelse(forsendelseId: Long): Map<String, DokumentMalDetaljer> {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        return forsendelse.behandlingInfo?.let {
            dokumentValgService.hentDokumentMalListe(
                HentDokumentValgRequest(
                    vedtakId = it.vedtakId,
                    behandlingId = it.behandlingId,
                    vedtakType = it.vedtakType,
                    behandlingType = it.toBehandlingType(),
                    soknadFra = it.soknadFra,
                    erFattetBeregnet = it.erFattetBeregnet,
                    erVedtakIkkeTilbakekreving = it.erVedtakIkkeTilbakekreving,
                    enhet = forsendelse.enhet
                )

            )
        } ?: forsendelseHarIngenBehandlingInfo(forsendelseId)
    }
}
