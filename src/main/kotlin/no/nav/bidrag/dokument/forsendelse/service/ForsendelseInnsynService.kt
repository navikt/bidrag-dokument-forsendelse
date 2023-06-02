package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.mapper.tilJournalpostDto
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.forsendelseHarIngenBehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBeskrivelse
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

val List<Forsendelse>.filtrerIkkeFerdigstiltEllerArkivert
    get() = this.filter { it.journalpostIdFagarkiv == null }.filter { it.status != ForsendelseStatus.SLETTET }

@Component
class ForsendelseInnsynTjeneste(
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val tilgangskontrollService: TilgangskontrollService,
    private val dokumentValgService: DokumentValgService,
    private val sakService: SakService
) {

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

        val journalpost = forsendelse.tilJournalpostDto()
        if (journalpost.innhold.isNullOrEmpty()) {
            val sak = sakService.hentSak(forsendelse.saksnummer)
            val gjelderRolle = sak?.roller?.find { it.fødselsnummer?.verdi == forsendelse.gjelderIdent }
            journalpost.innhold = forsendelse.behandlingInfo?.tilBeskrivelse(gjelderRolle?.type) ?: "Forsendelse ${forsendelse.forsendelseId}"
        }

        return journalpost
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
            .map(Forsendelse::tilForsendelseRespons)
    }

    fun hentForsendelseForSoknad(soknadId: String): List<ForsendelseResponsTo> {
        val forsendelser = forsendelseTjeneste.hentAlleMedSoknadId(soknadId)

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
                HentDokumentValgRequest(
                    vedtakId = it.vedtakId,
                    behandlingId = it.behandlingId,
                    vedtakType = it.vedtakType,
                    behandlingType = it.toBehandlingType(),
                    soknadFra = it.soknadFra,
                    erFattetBeregnet = it.erFattetBeregnet,
                    enhet = forsendelse.enhet
                ),

                )
        } ?: forsendelseHarIngenBehandlingInfo(forsendelseId)
    }
}
