package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hentHoveddokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilMottaker
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.valider
import org.springframework.stereotype.Component
import javax.transaction.Transactional


@Component
class OpprettForsendelseTjeneste(val dokumentBestillingTjeneste: DokumentBestillingTjeneste, val forsendelseTjeneste: ForsendelseTjeneste, val dokumenttjeneste: DokumentTjeneste, val saksbehandlerInfoManager: SaksbehandlerInfoManager) {

    @Transactional
    fun opprettForsendelse(forespørsel: OpprettForsendelseForespørsel): OpprettForsendelseRespons {

        forespørsel.valider()
        val forsendelse = opprettForsendelseFraForespørsel(forespørsel)

        val dokumenter = dokumenttjeneste.opprettNyDokument(forsendelse, forespørsel.dokumenter)

        return OpprettForsendelseRespons(
            forsendelseId = forsendelse.forsendelseId,
            dokumenter = dokumenter.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel
                )
            }
        )
    }

    private fun hentForsendelseType(forespørsel: OpprettForsendelseForespørsel): ForsendelseType{
        val dokumentmalDetaljer = dokumentBestillingTjeneste.hentDokumentmalDetaljer()
        return when(forespørsel.forsendelseType){
            ForsendelseTypeTo.UTGÅENDE -> ForsendelseType.UTGÅENDE
            ForsendelseTypeTo.NOTAT -> ForsendelseType.NOTAT
            else -> {
                val dokumentmalId = forespørsel.dokumenter.hentHoveddokument().dokumentmalId
                return when(dokumentmalDetaljer[dokumentmalId]?.type){
                    DokumentMalType.NOTAT -> ForsendelseType.NOTAT
                    else -> ForsendelseType.UTGÅENDE
                }
            }
        }
    }

    private fun opprettForsendelseFraForespørsel(forespørsel: OpprettForsendelseForespørsel): Forsendelse{

        val bruker = saksbehandlerInfoManager.hentSaksbehandler()
        val forsendelse = Forsendelse(
            saksnummer = forespørsel.saksnummer,
            forsendelseType = hentForsendelseType(forespørsel),
            gjelderIdent = forespørsel.gjelderIdent,
            enhet = forespørsel.enhet,
            språk = forespørsel.språk ?: "NB",
            opprettetAvIdent = bruker?.ident ?: "UKJENT",
            endretAvIdent = bruker?.ident ?: "UKJENT",
            opprettetAvNavn = bruker?.navn,
            mottaker = forespørsel.mottaker?.tilMottaker()
        )

        return forsendelseTjeneste.lagre(forsendelse)
    }
}