package no.nav.bidrag.dokument.forsendelse.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.consumer.BidragPersonConsumer
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilMottakerDo
import no.nav.bidrag.dokument.forsendelse.mapper.tilForsendelseType
import no.nav.bidrag.dokument.forsendelse.model.ConflictException
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.model.ugyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.ForsendelseMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.valider
import no.nav.bidrag.dokument.forsendelse.utvidelser.harNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.tilBehandlingInfo
import no.nav.bidrag.transport.dokument.forsendelse.DokumentRespons
import no.nav.bidrag.transport.dokument.forsendelse.ForsendelseConflictResponse
import no.nav.bidrag.transport.dokument.forsendelse.JournalTema
import no.nav.bidrag.transport.dokument.forsendelse.OpprettDokumentForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseForespørsel
import no.nav.bidrag.transport.dokument.forsendelse.OpprettForsendelseRespons
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
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
    fun behandleDataIntegrityException(
        e: DataIntegrityViolationException,
        request: OpprettForsendelseForespørsel,
    ): Nothing {
        if (e.cause is ConstraintViolationException) {
            val unikReferanse = request.unikReferanse
            val psqlException = (e.cause as ConstraintViolationException).sqlException
            // 23505 betyr unique violation i postgres
            if (unikReferanse != null && psqlException.sqlState == "23505") {
                val forsendelseId = forsendelseTjeneste.hentForsendelseMedUnikReferanse(unikReferanse)?.forsendelseId

                if (forsendelseId != null) {
                    log.error {
                        "Feil ved lagring av forsendelse. Det finnes allerede et forsendelse unik referansen ${request.unikReferanse} med forsendelseId $forsendelseId"
                    }
                    secureLogger.error {
                        "Feil ved lagring av forsendelse. " +
                            "Det finnes allerede et forsendelse med unik referansen ${request.unikReferanse}. " +
                            "Id: $forsendelseId. Request: $request"
                    }
                    throw ConflictException(
                        "Et forsendelse med angitt unikReferanse finnes allerede",
                        ForsendelseConflictResponse(forsendelseId),
                    )
                }
            }
        }
        log.error { "Uventet feil ved lagring av forsendelse" }
        secureLogger.error(e) { "Uventet feil ved lagring av forsendelse: ${e.message}" }
        throw e
    }

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

        log.debug {
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
                unikReferanse = forespørsel.unikReferanse,
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
                mottaker = forespørsel.mottaker!!.tilMottakerDo(mottakerInfo, mottakerSpråk),
                status = if (forespørsel.dokumenter.isEmpty()) ForsendelseStatus.UNDER_OPPRETTELSE else ForsendelseStatus.UNDER_PRODUKSJON,
                tema =
                    when (forespørsel.tema) {
                        JournalTema.FAR -> ForsendelseTema.FAR
                        else -> ForsendelseTema.BID
                    },
            )

        if (forespørsel.distribuerAutomatiskEtterFerdigstilling) {
            val metadata = ForsendelseMetadataDo()
            metadata.markerDistribuerAutomatisk()
            forsendelse.metadata = metadata
        }

        return forsendelseTjeneste.lagre(forsendelse)
    }
}
