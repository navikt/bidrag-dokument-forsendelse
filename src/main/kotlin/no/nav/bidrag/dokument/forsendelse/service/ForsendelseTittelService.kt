package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBehandlingInfo
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBeskrivelse
import org.springframework.stereotype.Service

@Service
class ForsendelseTittelService(
    private val sakService: SakService,
    private val vedtakConsumer: BidragVedtakConsumer
) {

    fun opprettForsendelseTittel(forsendelse: Forsendelse): String {
        val sak = sakService.hentSak(forsendelse.saksnummer)
        val vedtak = forsendelse.behandlingInfo?.vedtakId?.let { vedtakConsumer.hentVedtak(it) }
        val gjelderRolle = sak?.roller?.find { it.fødselsnummer?.verdi == forsendelse.gjelderIdent }
        return forsendelse.behandlingInfo?.tilBeskrivelse(gjelderRolle?.type, vedtak)
            ?: forsendelse.dokumenter.hoveddokument?.tittel ?: "Forsendelse ${forsendelse.forsendelseId}"
    }

    fun opprettForsendelseTittel(opprettForsendelseForespørsel: OpprettForsendelseForespørsel): String? {
        val sak = sakService.hentSak(opprettForsendelseForespørsel.saksnummer)
        val vedtak = opprettForsendelseForespørsel.behandlingInfo?.vedtakId?.let { vedtakConsumer.hentVedtak(it) }
        val gjelderRolle = sak?.roller?.find { it.fødselsnummer?.verdi == opprettForsendelseForespørsel.gjelderIdent }
        return opprettForsendelseForespørsel.tilBehandlingInfo()?.tilBeskrivelse(gjelderRolle?.type, vedtak)
            ?: opprettForsendelseForespørsel.dokumenter.firstOrNull()?.tittel
    }

}