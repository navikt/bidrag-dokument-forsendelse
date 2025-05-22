package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragSamhandlerConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.gjelderKlage
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBehandlingInfo
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBeskrivelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBeskrivelseBehandlingType
import no.nav.bidrag.domene.ident.SamhandlerId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ForsendelseTittelService(
    private val sakService: SakService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val behandlingConsumer: BidragBehandlingConsumer,
    private val dokumentBestillingConsumer: BidragDokumentBestillingConsumer,
    private val samhandlerConsumer: BidragSamhandlerConsumer,
    @Value("\${HENT_DOKUMENTVALG_DETALJER_FRA_VEDTAK_BEHANDLING_ENABLED:false}")
    private val hentDetaljerFraVedtakBehandlingEnabled: Boolean,
) {
    fun opprettDokumentTittel(
        forsendelse: OpprettForsendelseForespørsel,
        dokument: OpprettDokumentForespørsel,
    ): String? {
        val sak = sakService.hentSak(forsendelse.saksnummer)
        val detaljer = dokumentBestillingConsumer.dokumentmalDetaljer()
        val dokumentMalId = dokument.dokumentmalId
        if (dokumentMalId.isNullOrEmpty()) return null
        val dokumentMal = detaljer[dokumentMalId] ?: return null
        val rolleGjelder = sak?.roller?.find { it.fødselsnummer?.verdi == forsendelse.mottaker?.ident }
        val gjelderRm = sak?.roller?.find { it.reellMottaker?.ident?.verdi == forsendelse.mottaker?.ident }?.reellMottaker
        val rolleNavn =
            when {
                gjelderRm != null && gjelderRm.verge -> "verge"
                // Kan være at RM er BM i saken
                gjelderRm != null && SamhandlerId(gjelderRm.ident.verdi).gyldig() -> {
                    samhandlerConsumer
                        .hentSamhandler(gjelderRm.ident.verdi)
                        ?.områdekode
                        ?.name
                        ?.lowercase() ?: ""
                }
                rolleGjelder != null -> rolleGjelder.type.name.lowercase()
                else -> return null
            }

        return "${dokumentMal.tittel} til $rolleNavn"
    }

    fun opprettForsendelseTittel(forsendelse: Forsendelse): String {
        val sak = sakService.hentSak(forsendelse.saksnummer)
        val vedtak =
            if (hentDetaljerFraVedtakBehandlingEnabled) {
                forsendelse.behandlingInfo?.vedtakId?.let {
                    vedtakConsumer.hentVedtak(it)
                }
            } else {
                null
            }
        val behandling =
            if (vedtak == null) {
                forsendelse.behandlingInfo?.behandlingId?.let {
                    behandlingConsumer.hentBehandling(
                        it,
                    )
                }
            } else {
                null
            }
        val gjelderRolle = sak?.roller?.find { it.fødselsnummer?.verdi == forsendelse.gjelderIdent }
        return forsendelse.behandlingInfo?.tilBeskrivelse(gjelderRolle?.type, vedtak, behandling)
            ?: forsendelse.dokumenter.hoveddokument?.tittel ?: "Forsendelse ${forsendelse.forsendelseId}"
    }

    fun opprettForsendelseTittel(forespørsel: OpprettForsendelseForespørsel): String? {
        val sak = sakService.hentSak(forespørsel.saksnummer)
        val vedtak =
            if (hentDetaljerFraVedtakBehandlingEnabled) {
                forespørsel.behandlingInfo?.vedtakId?.let {
                    vedtakConsumer.hentVedtak(it)
                }
            } else {
                null
            }
        val behandling =
            if (vedtak == null) {
                forespørsel.behandlingInfo?.behandlingId?.let {
                    behandlingConsumer.hentBehandling(
                        it,
                    )
                }
            } else {
                null
            }
        val gjelderRolle = sak?.roller?.find { it.fødselsnummer?.verdi == forespørsel.gjelderIdent }
        return forespørsel.tilBehandlingInfo()?.tilBeskrivelse(gjelderRolle?.type, vedtak, behandling)
    }

    fun opprettForsendelseBehandlingPrefiks(behandlingInfo: BehandlingInfo?): String? {
        val vedtak = if (hentDetaljerFraVedtakBehandlingEnabled) behandlingInfo?.vedtakId?.let { vedtakConsumer.hentVedtak(it) } else null
        val behandling = if (vedtak == null) behandlingInfo?.behandlingId?.let { behandlingConsumer.hentBehandling(it) } else null
        val gjelderKlage = behandlingInfo?.gjelderKlage(vedtak, behandling) ?: false
        val klagePostfiks = if (gjelderKlage) " klage" else ""
        val behandlingType = behandlingInfo?.tilBeskrivelseBehandlingType(vedtak, behandling)
        return behandlingType?.let { "$it$klagePostfiks" }
    }

    fun hentTittelMedPrefiks(
        originalTittel: String,
        forespørsel: BehandlingInfo?,
    ): String {
        val tittelPrefiks = opprettForsendelseBehandlingPrefiks(forespørsel)
        return tittelPrefiks?.let { "$it, $originalTittel" } ?: originalTittel
    }
}
