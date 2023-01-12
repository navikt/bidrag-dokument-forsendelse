package no.nav.bidrag.dokument.forsendelse.tjeneste

import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilMottakerDo
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.validering.ForespørselValidering.valider
import no.nav.bidrag.dokument.forsendelse.tjeneste.validering.ForespørselValidering.validerKanEndreForsendelse
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentHoveddokument
import org.springframework.stereotype.Component
import javax.transaction.Transactional

private val log = KotlinLogging.logger {}

@Component
class OpprettForsendelseTjeneste(
    private val tilgangskontrollTjeneste: TilgangskontrollTjeneste,
    private val dokumentBestillingTjeneste: DokumentBestillingTjeneste,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val dokumenttjeneste: DokumentTjeneste,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager
) {

    @Transactional
    fun opprettForsendelse(forespørsel: OpprettForsendelseForespørsel): OpprettForsendelseRespons {
        tilgangskontrollTjeneste.sjekkTilgangPerson(forespørsel.gjelderIdent)
        tilgangskontrollTjeneste.sjekkTilgangSak(forespørsel.saksnummer)
        val forsendelseType = hentForsendelseType(forespørsel)
        forespørsel.valider(hentForsendelseType(forespørsel))
        val forsendelse = opprettForsendelseFraForespørsel(forespørsel, forsendelseType)

        val dokumenter = dokumenttjeneste.opprettNyttDokument(forsendelse, forespørsel.dokumenter)

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

    private fun opprettForsendelseFraForespørsel(forespørsel: OpprettForsendelseForespørsel, forsendelseType: ForsendelseType): Forsendelse{

        val bruker = saksbehandlerInfoManager.hentSaksbehandler()
        val forsendelse = Forsendelse(
            saksnummer = forespørsel.saksnummer,
            forsendelseType = forsendelseType,
            gjelderIdent = forespørsel.gjelderIdent,
            enhet = forespørsel.enhet,
            språk = forespørsel.språk ?: "NB",
            opprettetAvIdent = bruker?.ident ?: "UKJENT",
            endretAvIdent = bruker?.ident ?: "UKJENT",
            opprettetAvNavn = bruker?.navn,
            mottaker = forespørsel.mottaker?.tilMottakerDo()
        )

        return forsendelseTjeneste.lagre(forsendelse)
    }
}