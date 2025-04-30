package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.gjelderKlage
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBehandlingInfo
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBeskrivelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBeskrivelseBehandlingType
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseForespørsel
import org.springframework.stereotype.Service

@Service
class ForsendelseTittelService(
    private val sakService: SakService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val behandlingConsumer: BidragBehandlingConsumer,
) {
    fun opprettForsendelseTittel(forsendelse: Forsendelse): String {
        val sak = sakService.hentSak(forsendelse.saksnummer)
        val vedtak = forsendelse.behandlingInfo?.vedtakId?.let { vedtakConsumer.hentVedtak(it) }
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
        val vedtak = forespørsel.behandlingInfo?.vedtakId?.let { vedtakConsumer.hentVedtak(it) }
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
        val vedtak = behandlingInfo?.vedtakId?.let { vedtakConsumer.hentVedtak(it) }
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
