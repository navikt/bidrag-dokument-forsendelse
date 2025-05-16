package no.nav.bidrag.dokument.forsendelse.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.consumer.BidragPersonConsumer
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilMottakerDo
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseType
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.model.ugyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.valider
import no.nav.bidrag.dokument.forsendelse.utvidelser.harNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBehandlingInfo
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class OpprettForsendelseService(
    private val tilgangskontrollService: TilgangskontrollService,
    private val dokumentBestillingService: DokumentBestillingService,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val personConsumer: BidragPersonConsumer,
    private val dokumenttjeneste: DokumentTjeneste,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val forsendelseTittelService: ForsendelseTittelService,
) {
    @Transactional
    fun opprettForsendelse(forespørsel: OpprettForsendelseForespørsel): OpprettForsendelseRespons {
        tilgangskontrollService.sjekkTilgangPerson(forespørsel.gjelderIdent)
        tilgangskontrollService.sjekkTilgangSak(forespørsel.saksnummer)
        val forsendelseType = hentForsendelseType(forespørsel)
        forespørsel.valider(forsendelseType)
        SIKKER_LOGG.info { "Oppretter forsendelse for forespørsel $forespørsel med forsendelseType $forsendelseType" }
        val forsendelse = opprettForsendelseFraForespørsel(forespørsel, forsendelseType)

        val dokumenter =
            dokumenttjeneste.opprettNyttDokument(forsendelse, dokumenterMedOppdatertTittel(forespørsel, forsendelseType))

        log.info {
            "Opprettet forsendelse ${forsendelse.forsendelseId} med dokumenter ${dokumenter.joinToString(
                ",",
            ) { it.dokumentreferanse }}"
        }
        return OpprettForsendelseRespons(
            forsendelseId = forsendelse.forsendelseId,
            forsendelseType = forsendelse.tilForsendelseType(),
            dokumenter =
                dokumenter.map {
                    DokumentRespons(
                        dokumentreferanse = it.dokumentreferanse,
                        tittel = it.tittel,
                        dokumentDato = it.dokumentDato,
                    )
                },
        )
    }

    private fun dokumenterMedOppdatertTittel(
        forespørsel: OpprettForsendelseForespørsel,
        forsendelseType: ForsendelseType,
    ): List<OpprettDokumentForespørsel> {
        val dokumenter = forespørsel.dokumenter
//        val skalLeggeTilPrefiksPåNotatTittel = forsendelseType == ForsendelseType.NOTAT && dokumenter.size == 1 && forespørsel.opprettTittel == true
//        if (skalLeggeTilPrefiksPåNotatTittel) {
//            val originalTittel = dokumenter[0].tittel
//            val tittelPrefiks = forsendelseTittelService.opprettForsendelseBehandlingPrefiks(forespørsel.tilBehandlingInfo())
//            val nyTittel = tittelPrefiks?.let { "$it, $originalTittel" } ?: originalTittel
//            return dokumenter.map { it.copy(tittel = nyTittel) }
//        }

        return dokumenter.mapIndexed { index, it ->
            if (it.tittel.isEmpty()) {
                it.copy(
                    tittel =
                        forsendelseTittelService.opprettDokumentTittel(forespørsel, it)
                            ?: ugyldigForespørsel("Tittel på dokument $index kan ikke være tom".replace("  ", "")),
                )
            } else {
                it
            }
        }
    }

    private fun hentForsendelseType(forespørsel: OpprettForsendelseForespørsel): ForsendelseType {
        if (forespørsel.dokumenter.isEmpty()) return ForsendelseType.UTGÅENDE
        val dokumentmalDetaljer = dokumentBestillingService.hentDokumentmalDetaljer()
        return forespørsel.dokumenter.harNotat(dokumentmalDetaljer).ifTrue { ForsendelseType.NOTAT }
            ?: ForsendelseType.UTGÅENDE
    }

    private fun opprettForsendelseFraForespørsel(
        forespørsel: OpprettForsendelseForespørsel,
        forsendelseType: ForsendelseType,
    ): Forsendelse {
        val bruker = saksbehandlerInfoManager.hentSaksbehandler()
        val mottakerIdent = forespørsel.mottaker!!.ident
        val mottakerInfo = mottakerIdent?.let { personConsumer.hentPerson(mottakerIdent) }
        val mottakerSpråk = forespørsel.språk ?: mottakerIdent?.let { personConsumer.hentPersonSpråk(mottakerIdent) } ?: "NB"
        val forsendelse =
            Forsendelse(
                saksnummer = forespørsel.saksnummer,
                batchId = if (forespørsel.batchId.isNullOrEmpty()) null else forespørsel.batchId,
                forsendelseType = forsendelseType,
                gjelderIdent = forespørsel.gjelderIdent,
                behandlingInfo = forespørsel.tilBehandlingInfo(),
                enhet = forespørsel.enhet,
                tittel =
                    if (forespørsel.opprettTittel == true && forsendelseType !== ForsendelseType.NOTAT) {
                        forsendelseTittelService.opprettForsendelseTittel(forespørsel)
                    } else {
                        null
                    },
                språk = mottakerSpråk,
                opprettetAvIdent = bruker?.ident ?: "UKJENT",
                endretAvIdent = bruker?.ident ?: "UKJENT",
                opprettetAvNavn = bruker?.navn,
                mottaker = forespørsel.mottaker.tilMottakerDo(mottakerInfo, mottakerSpråk),
                status = if (forespørsel.dokumenter.isEmpty()) ForsendelseStatus.UNDER_OPPRETTELSE else ForsendelseStatus.UNDER_PRODUKSJON,
                tema =
                    when (forespørsel.tema) {
                        JournalTema.FAR -> ForsendelseTema.FAR
                        else -> ForsendelseTema.BID
                    },
            )

        return forsendelseTjeneste.lagre(forsendelse)
    }
}
