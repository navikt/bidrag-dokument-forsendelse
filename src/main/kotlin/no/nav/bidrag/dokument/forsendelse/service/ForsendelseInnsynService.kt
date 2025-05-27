package no.nav.bidrag.dokument.forsendelse.service

import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseIkkeDistribuertResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.mapper.DokumentDtoMetadata
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.mapper.tilJournalpostDto
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.erBatchForsendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.erBatchForsendelseIkkeDistribuertEldreEnn3Dager
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.transport.dokument.JournalpostDto
import no.nav.bidrag.transport.dokument.JournalpostResponse
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

val List<Forsendelse>.filtrerIkkeFerdigstiltEllerArkivert
    get() = this.filter { it.journalpostIdFagarkiv == null }.filter { it.status != ForsendelseStatus.SLETTET }

@Component
class ForsendelseInnsynService(
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val tilgangskontrollService: TilgangskontrollService,
    private val dokumentValgService: DokumentValgService,
    private val dokumentTjeneste: DokumentTjeneste,
    private val forsendelseTittelService: ForsendelseTittelService,
    val unleash: Unleash,
) {
    fun hentForsendelserIkkeDistribuert(): List<ForsendelseIkkeDistribuertResponsTo> {
        val journalpostDtoer =
            forsendelseTjeneste
                .hentForsendelserOpprettetFørDagensDatoIkkeDistribuert()
                .filter { it.tema == ForsendelseTema.BID || tilgangskontrollService.harTilgangTilTema(it.tema.name) }
                .filter {
                    !it.erBatchForsendelse() ||
                        it.erBatchForsendelseIkkeDistribuertEldreEnn3Dager() ||
                        unleash.isEnabled("forsendelse.batchbrev_nyere_enn_3_dager")
                }.map {
                    ForsendelseIkkeDistribuertResponsTo(
                        enhet = it.enhet,
                        forsendelseId = it.forsendelseIdMedPrefix,
                        saksnummer = it.saksnummer,
                        opprettetDato = it.opprettetTidspunkt,
                        tittel = it.dokumenter.hoveddokument?.tittel,
                    )
                }
        log.info { "Hentet ${journalpostDtoer.size} utgående forsendelser som ikke er distribuert" }
        return journalpostDtoer
    }

    fun hentForsendelseForSakJournal(
        saksnummer: String,
        temaListe: List<JournalTema> = listOf(JournalTema.BID),
    ): List<JournalpostDto> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)
        val forsendelserFiltrert =
            forsendelser.filtrerIkkeFerdigstiltEllerArkivert
                .filter { temaListe.map { jt -> jt.name }.contains(it.tema.name) }
                .filter { tilgangskontrollService.harTilgangTilTema(it.tema.name) }
                .filter {
                    !it.erBatchForsendelse() ||
                        it.erBatchForsendelseIkkeDistribuertEldreEnn3Dager() ||
                        unleash.isEnabled("forsendelse.batchbrev_nyere_enn_3_dager")
                }.map { tilJournalpostDto(it) }

        log.info { "Hentet ${forsendelserFiltrert.size} forsendelser for sak $saksnummer og temaer $temaListe" }
        return forsendelserFiltrert
    }

    fun tilJournalpostDto(forsendelse: Forsendelse): JournalpostDto {
        val journalpostDto = forsendelse.tilJournalpostDto(tilDokumenterMetadata(forsendelse.dokumenter))
        return journalpostDto.innhold.isNullOrEmpty().ifTrue {
            journalpostDto.copy(
                innhold = forsendelseTittelService.opprettForsendelseTittel(forsendelse),
            )
        } ?: journalpostDto
    }

    private fun tilDokumenterMetadata(dokumenter: List<Dokument>): Map<String, DokumentDtoMetadata> =
        dokumenter.associate {
            it.dokumentreferanse to
                run {
                    val metadata = DokumentDtoMetadata()
                    if (it.erLenkeTilEnAnnenForsendelse) {
                        val originalDokument = dokumentTjeneste.hentOriginalDokument(it)
                        if (originalDokument.erFraAnnenKilde) {
                            metadata.oppdaterOriginalDokumentreferanse(originalDokument.dokumentreferanseOriginal)
                            metadata.oppdaterOriginalJournalpostId(originalDokument.journalpostIdOriginal)
                        } else {
                            metadata.oppdaterOriginalDokumentreferanse(originalDokument.dokumentreferanse)
                            metadata.oppdaterOriginalJournalpostId(originalDokument.forsendelseId?.toString())
                        }
                    } else {
                        metadata.oppdaterOriginalDokumentreferanse(it.dokumentreferanseOriginal)
                        metadata.oppdaterOriginalJournalpostId(it.journalpostIdOriginal)
                    }
                    metadata.copy()
                }
        }

    fun hentForsendelseJournal(
        forsendelseId: Long,
        saksnummer: String? = null,
    ): JournalpostResponse {
        val forsendelse =
            forsendelseTjeneste.medForsendelseId(forsendelseId)
                ?: fantIkkeForsendelse(forsendelseId)

        if (!saksnummer.isNullOrEmpty() && saksnummer != forsendelse.saksnummer) fantIkkeForsendelse(forsendelseId, saksnummer)

        log.debug { "Hentet forsendelse $forsendelseId med saksnummer ${forsendelse.saksnummer}" }

        return JournalpostResponse(
            journalpost = forsendelse.tilJournalpostDto(tilDokumenterMetadata(forsendelse.dokumenter)),
            sakstilknytninger = listOf(forsendelse.saksnummer),
        )
    }

    fun hentForsendelseForSak(saksnummer: String): List<ForsendelseResponsTo> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.filtrerIkkeFerdigstiltEllerArkivert
            .map { it.tilForsendelseRespons(tilDokumenterMetadata(it.dokumenter)) }
    }

    fun hentForsendelse(
        forsendelseId: Long,
        saksnummer: String? = null,
    ): ForsendelseResponsTo {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        if (!saksnummer.isNullOrEmpty() && saksnummer != forsendelse.saksnummer) fantIkkeForsendelse(forsendelseId, saksnummer)

        return forsendelse.tilForsendelseRespons(tilDokumenterMetadata(forsendelse.dokumenter))
    }

    fun tilForsendelseRespons(forsendelse: Forsendelse): ForsendelseResponsTo {
        val forsendelseRespons = forsendelse.tilForsendelseRespons(tilDokumenterMetadata(forsendelse.dokumenter))
        return forsendelseRespons.tittel.isNullOrEmpty().ifTrue {
            forsendelseRespons.copy(
                tittel = forsendelseTittelService.opprettForsendelseTittel(forsendelse),
            )
        } ?: forsendelseRespons
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
                    enhet = forsendelse.enhet,
                    soknadType = it.soknadType,
                ),
            )
        } ?: dokumentValgService.hentDokumentMalListe()
    }
}
