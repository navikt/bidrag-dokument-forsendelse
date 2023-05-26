package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.behandling.felles.enums.VedtakKilde
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.mapper.tilJournalpostDto
import no.nav.bidrag.dokument.forsendelse.model.KLAGE_ANKE_ENHET_KODER
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.forsendelseHarIngenBehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.Forvaltning
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.VedtakStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

val List<Forsendelse>.filtrerIkkeFerdigstiltEllerArkivert
    get() = this.filter { it.journalpostIdFagarkiv == null }.filter { it.status != ForsendelseStatus.SLETTET }

@Component
class ForsendelseInnsynTjeneste(
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val tilgangskontrollService: TilgangskontrollService,
    private val dokumentValgService: DokumentValgService
) {

    fun hentForsendelseForSakJournal(saksnummer: String, temaListe: List<JournalTema> = listOf(JournalTema.BID)): List<JournalpostDto> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)
        val forsendelserFiltrert = forsendelser.filtrerIkkeFerdigstiltEllerArkivert
            .filter { temaListe.map { jt -> jt.name }.contains(it.tema.name) }
            .filter { tilgangskontrollService.harTilgangTilTema(it.tema.name) }
            .map(Forsendelse::tilJournalpostDto)

        log.info { "Hentet ${forsendelserFiltrert.size} forsendelser for sak $saksnummer og temaer $temaListe" }
        return forsendelserFiltrert
    }

    fun hentForsendelseJournal(forsendelseId: Long, saksnummer: String? = null): JournalpostResponse {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)

        if (!saksnummer.isNullOrEmpty() && saksnummer != forsendelse.saksnummer) fantIkkeForsendelse(forsendelseId, saksnummer)

        log.debug { "Hentet forsendelse $forsendelseId med saksnummer ${forsendelse.saksnummer}" }

        return JournalpostResponse(
            journalpost = forsendelse.tilJournalpostDto(),
            sakstilknytninger = listOf(forsendelse.saksnummer)
        )
    }

    fun hentForsendelseForSak(saksnummer: String): List<ForsendelseResponsTo> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.filtrerIkkeFerdigstiltEllerArkivert
            .map(Forsendelse::tilForsendelseRespons)
    }

    fun hentForsendelse(forsendelseId: Long, saksnummer: String? = null): ForsendelseResponsTo {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        if (!saksnummer.isNullOrEmpty() && saksnummer != forsendelse.saksnummer) fantIkkeForsendelse(forsendelseId, saksnummer)

        return forsendelse.tilForsendelseRespons()
    }

    fun hentDokumentvalgForsendelse(forsendelseId: Long): Map<String, DokumentMalDetaljer> {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        return forsendelse.behandlingInfo?.let {
            dokumentValgService.hentDokumentMalListe(
                it.vedtakType,
                it.toBehandlingType(),
                it.soknadFra,
                it.toVedtakStatus(),
                forsendelse.toForvaltning()
            )
        } ?: forsendelseHarIngenBehandlingInfo(forsendelseId)
    }

    private fun Forsendelse.toForvaltning(): Forvaltning {
        return if (KLAGE_ANKE_ENHET_KODER.contains(enhet)) Forvaltning.KLAGE_ANKE else Forvaltning.BIDRAG
    }

    private fun BehandlingInfo.toVedtakStatus(): VedtakStatus {
        return when (this.vedtakKilde) {
            VedtakKilde.MANUELT -> VedtakStatus.FATTET_MANUELT
            VedtakKilde.AUTOMATISK -> VedtakStatus.FATTET_AUTOMATISK
            else -> VedtakStatus.IKKE_FATTET
        }
    }
}
