package no.nav.bidrag.dokument.forsendelse.service

import jakarta.transaction.Transactional
import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.erForsendelse
import no.nav.bidrag.dokument.forsendelse.consumer.BidragPersonConsumer
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilMottakerDo
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseType
import no.nav.bidrag.dokument.forsendelse.mapper.tilOpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.valider
import no.nav.bidrag.dokument.forsendelse.utvidelser.harNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class OpprettForsendelseService(
    private val tilgangskontrollService: TilgangskontrollService,
    private val dokumentBestillingService: DokumentBestillingService,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val personConsumer: BidragPersonConsumer,
    private val dokumenttjeneste: DokumentTjeneste,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager
) {

    @Transactional
    fun opprettForsendelse(forespørsel: OpprettForsendelseForespørsel): OpprettForsendelseRespons {
        tilgangskontrollService.sjekkTilgangPerson(forespørsel.gjelderIdent)
        tilgangskontrollService.sjekkTilgangSak(forespørsel.saksnummer)
        val forsendelseType = hentForsendelseType(forespørsel)
        forespørsel.valider(forsendelseType)
        val forsendelse = opprettForsendelseFraForespørsel(forespørsel, forsendelseType)

        val dokumenter =
            dokumenttjeneste.opprettNyttDokument(forsendelse, forespørsel.dokumenter)

        log.info { "Opprettet forsendelse ${forsendelse.forsendelseId} med dokumenter ${dokumenter.joinToString(",") { it.dokumentreferanse }}" }
        return OpprettForsendelseRespons(
            forsendelseId = forsendelse.forsendelseId,
            forsendelseType = forsendelse.tilForsendelseType(),
            dokumenter = dokumenter.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel,
                    dokumentDato = it.dokumentDato
                )
            }
        )
    }

    private fun hentForsendelseType(forespørsel: OpprettForsendelseForespørsel): ForsendelseType {
        if (forespørsel.dokumenter.isEmpty()) return ForsendelseType.UTGÅENDE
        val dokumentmalDetaljer = dokumentBestillingService.hentDokumentmalDetaljer()
        return forespørsel.dokumenter.harNotat(dokumentmalDetaljer).ifTrue { ForsendelseType.NOTAT }
            ?: ForsendelseType.UTGÅENDE
    }

    private fun opprettForsendelseFraForespørsel(forespørsel: OpprettForsendelseForespørsel, forsendelseType: ForsendelseType): Forsendelse {
        val bruker = saksbehandlerInfoManager.hentSaksbehandler()
        val mottakerIdent = forespørsel.mottaker!!.ident!!
        val mottakerInfo = personConsumer.hentPerson(mottakerIdent)
        val mottakerSpråk = forespørsel.språk ?: personConsumer.hentPersonSpråk(mottakerIdent) ?: "NB"
        val forsendelse = Forsendelse(
            saksnummer = forespørsel.saksnummer,
            batchId = if (forespørsel.batchId.isNullOrEmpty()) null else forespørsel.batchId,
            forsendelseType = forsendelseType,
            gjelderIdent = forespørsel.gjelderIdent,
            behandlingInfo = forespørsel.behandlingInfo?.let {
                BehandlingInfo(
                    behandlingId = it.behandlingId,
                    vedtakId = it.vedtakId,
                    soknadId = it.soknadId,
                    engangsBelopType = it.engangsBelopType,
                    vedtakType = it.vedtakType,
                    stonadType = it.stonadType,
                    soknadFra = it.soknadFra,
                    erFattetBeregnet = it.erFattetBeregnet
                )
            },
            enhet = forespørsel.enhet,
            språk = mottakerSpråk,
            opprettetAvIdent = bruker?.ident ?: "UKJENT",
            endretAvIdent = bruker?.ident ?: "UKJENT",
            opprettetAvNavn = bruker?.navn,
            mottaker = forespørsel.mottaker.tilMottakerDo(mottakerInfo, mottakerSpråk),
            status = if (forespørsel.dokumenter.isEmpty()) ForsendelseStatus.UNDER_OPPRETTELSE else ForsendelseStatus.UNDER_PRODUKSJON,
            tema = when (forespørsel.tema) {
                JournalTema.FAR -> ForsendelseTema.FAR
                else -> ForsendelseTema.BID
            }
        )

        return forsendelseTjeneste.lagre(forsendelse)
    }

    // Lenke dokumenter mellom forsendelser
    private fun List<OpprettDokumentForespørsel>.konverterTilOpprettDokumentForespørselMedLenketDokument(): List<OpprettDokumentForespørsel> {
        return this.flatMap { it.konverterTilOpprettDokumentForespørselMedLenketDokument() }
    }

    private fun OpprettDokumentForespørsel.konverterTilOpprettDokumentForespørselMedLenketDokument(): List<OpprettDokumentForespørsel> {
        return if (this.journalpostId?.erForsendelse == true) this.konverterTilOpprettDokumentForespørselMedOriginalLenketDokumenter()
        else listOf(this)
    }

    private fun Dokument.opprettDokumentForespørselMedOriginalDokument() = dokumenttjeneste.hentOriginalDokument(this).tilOpprettDokumentForespørsel()

    private fun OpprettDokumentForespørsel.konverterTilOpprettDokumentForespørselMedOriginalLenketDokumenter(): List<OpprettDokumentForespørsel> {
        val dokumentForsendelse =
            this.journalpostId?.erForsendelse?.let { forsendelseTjeneste.medForsendelseId(this.journalpostId.numerisk) }
                ?: return listOf(this)

        return if (this.dokumentreferanse.isNullOrEmpty()) dokumentForsendelse.dokumenter.map { dok -> dok.opprettDokumentForespørselMedOriginalDokument() }
        else {
            val dokumentLenket = dokumentForsendelse.dokumenter.hentDokument(this.dokumentreferanse)!!
            listOf(dokumentLenket.opprettDokumentForespørselMedOriginalDokument())
        }

    }
}
