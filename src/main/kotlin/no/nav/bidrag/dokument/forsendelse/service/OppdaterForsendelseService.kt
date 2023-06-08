package no.nav.bidrag.dokument.forsendelse.service

import jakarta.transaction.Transactional
import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDtoIdType
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.dto.MottakUtsendingKanal
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.erForsendelse
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragPersonConsumer
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilMottakerDo
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.tilOpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.mapper.ForespørselMapper.toForsendelseTema
import no.nav.bidrag.dokument.forsendelse.mapper.tilArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.mapper.tilDokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.model.numerisk
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.valider
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanEndreForsendelse
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanFerdigstilleForsendelse
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanLeggeTilDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumentDato
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumenterIkkeSlettet
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumenterLogiskSlettet
import no.nav.bidrag.dokument.forsendelse.utvidelser.erNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.skalDokumentSlettes
import no.nav.bidrag.dokument.forsendelse.utvidelser.sortertEtterRekkefølge
import no.nav.bidrag.dokument.forsendelse.utvidelser.validerGyldigEndring
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Component
@Transactional
class OppdaterForsendelseService(
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val dokumentTjeneste: DokumentTjeneste,
    private val personConsumer: BidragPersonConsumer,
    private val bidragDokumentConsumer: BidragDokumentConsumer,
    private val fysiskDokumentService: FysiskDokumentService
) {

    fun oppdaterForsendelse(
        forsendelseId: Long,
        forespørsel: OppdaterForsendelseForespørsel
    ): OppdaterForsendelseResponse {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        forespørsel.validerGyldigEndring(forsendelse)

        log.info { "Oppdaterer forsendelse $forsendelseId" }

        val oppdatertForsendelse = if (forsendelse.status == ForsendelseStatus.UNDER_OPPRETTELSE) {
            val mottaker = forespørsel.mottaker?.let {
                val mottakerIdent = it.ident
                val mottakerInfo = mottakerIdent?.let { personConsumer.hentPerson(mottakerIdent) }
                val mottakerSpråk = forespørsel.språk ?: mottakerIdent?.let { personConsumer.hentPersonSpråk(mottakerIdent) } ?: forsendelse.språk
                forespørsel.mottaker.tilMottakerDo(mottakerInfo, mottakerSpråk)
            }

            forsendelseTjeneste.lagre(
                forsendelse.copy(
                    mottaker = mottaker ?: forsendelse.mottaker,
                    språk = forespørsel.språk ?: forsendelse.språk,
                    enhet = forespørsel.enhet ?: forsendelse.enhet,
                    tema = forespørsel.tema?.toForsendelseTema() ?: forsendelse.tema,
                    status = ForsendelseStatus.UNDER_PRODUKSJON,
                    dokumenter = oppdaterOgOpprettDokumenter(forsendelse, forespørsel)
                )
            )
        } else forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = oppdaterOgOpprettDokumenter(forsendelse, forespørsel)
            )
        )

        return OppdaterForsendelseResponse(
            forsendelseId = oppdatertForsendelse.forsendelseId.toString(),
            dokumenter = oppdatertForsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel,
                    dokumentDato = it.dokumentDato
                )
            }
        )
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun ferdigstillOgHentForsendelse(
        forsendelseId: Long,
        lokalUtskrift: Boolean = false
    ): Forsendelse? {
        ferdigstillForsendelse(forsendelseId, lokalUtskrift)
        return forsendelseTjeneste.medForsendelseId(forsendelseId)
    }

    @Transactional
    fun ferdigstillForsendelse(
        forsendelseId: Long,
        lokalUtskrift: Boolean = false
    ): OpprettJournalpostResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanFerdigstilleForsendelse()
        log.info { "Ferdigstiller forsendelse $forsendelseId med type ${forsendelse.forsendelseType} og tema ${forsendelse.tema}." }

        val opprettJournalpostRequest = OpprettJournalpostRequest(
            avsenderMottaker = if (!forsendelse.erNotat) {
                AvsenderMottakerDto(
                    ident = forsendelse.mottaker!!.ident,
                    navn = forsendelse.mottaker.navn,
                    type = when (forsendelse.mottaker.identType) {
                        MottakerIdentType.SAMHANDLER -> AvsenderMottakerDtoIdType.SAMHANDLER
                        else -> AvsenderMottakerDtoIdType.FNR
                    }
                )
            } else {
                null
            },
            referanseId = "BIF_${forsendelse.forsendelseId}",
            gjelderIdent = forsendelse.gjelderIdent,
            journalførendeEnhet = forsendelse.enhet,
            journalposttype = when (forsendelse.forsendelseType) {
                ForsendelseType.UTGÅENDE -> JournalpostType.UTGÅENDE
                ForsendelseType.NOTAT -> JournalpostType.NOTAT
            },
            kanal = if (lokalUtskrift) MottakUtsendingKanal.LOKAL_UTSKRIFT else null,
            dokumenter = forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                OpprettDokumentDto(
                    brevkode = it.dokumentmalId,
                    tittel = it.tittel,
                    fysiskDokument = fysiskDokumentService.hentFysiskDokument(it)
                )
            },
            tilknyttSaker = listOf(forsendelse.saksnummer),
            saksbehandlerIdent = if (saksbehandlerInfoManager.erApplikasjonBruker()) forsendelse.opprettetAvIdent else null,
            skalFerdigstilles = true,
            tema = when (forsendelse.tema) {
                ForsendelseTema.FAR -> "FAR"
                else -> "BID"
            },
            datoDokument = if (forsendelse.erNotat) forsendelse.dokumentDato else null
        )

        val respons = bidragDokumentConsumer.opprettJournalpost(opprettJournalpostRequest)

        forsendelseTjeneste.lagre(
            forsendelse.copy(
                journalpostIdFagarkiv = respons!!.journalpostId,
                status = ForsendelseStatus.FERDIGSTILT,
                dokumenter = forsendelse.dokumenter.mapIndexed { i, it ->
                    it.copy(
                        dokumentreferanseFagarkiv = if (respons.dokumenter.size > i) respons.dokumenter[i].dokumentreferanse else null
                    )
                },
                ferdigstiltTidspunkt = LocalDateTime.now()
            )
        )

        log.info { "Ferdigstilt og opprettet journalpost for forsendelse $forsendelseId med type ${forsendelse.forsendelseType}. Opprettet journalpostId=${respons.journalpostId}." }

        return respons
    }

    fun fjernDokumentFraForsendelse(
        forsendelseId: Long,
        dokumentreferanse: String
    ): OppdaterForsendelseResponse? {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return null
        forsendelse.validerKanEndreForsendelse()

        val oppdaterteDokumenter = forsendelse.dokumenter
            .filter { it.dokumentreferanse != dokumentreferanse || it.dokumentreferanseOriginal == null }
            .map {
                it.copy(
                    slettetTidspunkt = if (it.dokumentreferanse == dokumentreferanse) LocalDate.now() else null
                )
            }

        if (oppdaterteDokumenter.isEmpty()) throw UgyldigForespørsel("Kan ikke slette alle dokumenter fra forsendelse")

        forsendelseTjeneste.lagre(
            forsendelse
                .copy(dokumenter = oppdaterteDokumenter.sortertEtterRekkefølge)
        )

        return OppdaterForsendelseResponse(
            forsendelseId = forsendelse.forsendelseId.toString(),
            dokumenter = forsendelse.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel,
                    dokumentDato = it.dokumentDato
                )
            }
        )
    }

    fun knyttDokumentTilForsendelse(
        forsendelseId: Long,
        forespørsel: OpprettDokumentForespørsel
    ): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        forespørsel.validerKanLeggeTilDokument(forsendelse)

        val nyDokument = knyttDokumentTilForsendelse(forsendelse, forespørsel)

        return DokumentRespons(
            dokumentreferanse = nyDokument.dokumentreferanse,
            tittel = nyDokument.tittel,
            journalpostId = nyDokument.journalpostId,
            dokumentDato = nyDokument.dokumentDato
        )
    }

    fun knyttDokumentTilForsendelse(
        forsendelse: Forsendelse,
        forespørsel: OpprettDokumentForespørsel
    ): Dokument {
        forsendelse.validerKanEndreForsendelse()
        forespørsel.validerKanLeggeTilDokument(forsendelse)

        val nyDokument = dokumentTjeneste.opprettNyttDokument(forsendelse, forespørsel)

        log.info { "Knyttet nytt dokument til ${forsendelse.forsendelseId} med tittel=${forespørsel.tittel}, språk=${forespørsel.språk} dokumentmalId=${forespørsel.dokumentmalId}, dokumentreferanse=${nyDokument.dokumentreferanse} og journalpostId=${nyDokument.journalpostId}" }

        return nyDokument
    }

    fun opphevFerdigstillDokument(
        forsendelse: Forsendelse,
        dokumentreferanse: String
    ): List<Dokument> {
        val oppdaterteDokumenter = forsendelse.dokumenter
            .map {
                if (it.dokumentreferanse == dokumentreferanse) {
                    it.copy(
                        dokumentStatus = when (it.dokumentStatus) {
                            DokumentStatus.MÅ_KONTROLLERES, DokumentStatus.KONTROLLERT -> DokumentStatus.MÅ_KONTROLLERES
                            else -> it.dokumentStatus
                        }
                    )
                } else {
                    it
                }
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    fun ferdigstillDokument(
        forsendelse: Forsendelse,
        dokumentreferanse: String
    ): List<Dokument> {
        val oppdaterteDokumenter = forsendelse.dokumenter
            .map {
                if (it.dokumentreferanse == dokumentreferanse) {
                    it.copy(
                        dokumentStatus = when (it.dokumentStatus) {
                            DokumentStatus.MÅ_KONTROLLERES, DokumentStatus.KONTROLLERT -> DokumentStatus.KONTROLLERT
                            DokumentStatus.UNDER_REDIGERING, DokumentStatus.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
                            else -> it.dokumentStatus
                        }
                    )
                } else {
                    it
                }
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    fun oppdaterDokument(
        forsendelse: Forsendelse,
        dokumentreferanse: String,
        forespørsel: OppdaterDokumentForespørsel
    ): List<Dokument> {
        val oppdaterteDokumenter = forsendelse.dokumenter
            .map {
                if (it.dokumentreferanse == dokumentreferanse) {
                    it.copy(
                        tittel = forespørsel.tittel ?: it.tittel,
                        dokumentDato = forespørsel.dokumentDato ?: it.dokumentDato
                    )
                } else {
                    it
                }
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    private fun oppdaterOgOpprettDokumenter(
        forsendelse: Forsendelse,
        forespørsel: OppdaterForsendelseForespørsel
    ): List<Dokument> {
        val logiskSlettetDokumenterFraForespørsel =
            forsendelse.dokumenter.filter { forespørsel.skalDokumentSlettes(it.dokumentreferanse) && !it.erFraAnnenKilde }
                .map {
                    it.copy(
                        slettetTidspunkt = LocalDate.now()
                    )
                }


        val oppdaterteDokumenter = forespørsel.dokumenter
            .filter { it.fjernTilknytning == false }
            .flatMapIndexed { indeks, it ->
                val eksisterendeDokument = forsendelse.dokumenter.hentDokument(it.dokumentreferanse)
                eksisterendeDokument?.copy(
                    tittel = it.tittel ?: eksisterendeDokument.tittel,
                    rekkefølgeIndeks = indeks,
                    dokumentDato = if (indeks == 0 && forsendelse.erNotat) {
                        forespørsel.dokumentDato
                            ?: eksisterendeDokument.dokumentDato
                    } else {
                        eksisterendeDokument.dokumentDato
                    }
                )?.let { listOf(it) } ?: it.konverterTilOpprettDokumentForespørsel().map { knyttDokumentTilForsendelse(forsendelse, it) }
            } + forsendelse.dokumenter.dokumenterLogiskSlettet + logiskSlettetDokumenterFraForespørsel

        if (oppdaterteDokumenter.dokumenterIkkeSlettet.isEmpty()) throw UgyldigForespørsel("Kan ikke slette alle dokumenter fra forsendelse")

        val fysiskSlettetDokumenter =
            forespørsel.dokumenter.filter { it.fjernTilknytning == true }.mapNotNull { forsendelse.dokumenter.hentDokument(it.dokumentreferanse) }

        val slettetDokumenter = logiskSlettetDokumenterFraForespørsel + fysiskSlettetDokumenter
        flyttLenkeTilNyDokumentHvisOriginalDokumentErSlettet(slettetDokumenter)
        validerIkkeLagtTilDuplikatDokument(oppdaterteDokumenter)
        return oppdaterteDokumenter.sortertEtterRekkefølge
    }

    private fun validerIkkeLagtTilDuplikatDokument(oppdaterteDokumenter: List<Dokument>) {
        oppdaterteDokumenter.forEach { oppdatertDokument ->
            val originalDokument = dokumentTjeneste.hentOriginalDokument(oppdatertDokument)
            oppdaterteDokumenter.filter { it.dokumentreferanse != originalDokument.dokumentreferanse }
                .any {
                    it.erFraAnnenKilde &&
                            it.dokumentreferanseOriginal == originalDokument.dokumentreferanseOriginal && it.journalpostIdOriginal == originalDokument.journalpostIdOriginal
                }
                .ifTrue {
                    throw UgyldigForespørsel(
                        "Kan ikke legge til samme dokument flere ganger til forsendelse." +
                                " Original dokument ${originalDokument.dokumentreferanse} med referanse til ${originalDokument.journalpostIdOriginal}:${originalDokument.dokumentreferanseOriginal}"
                    )
                }
        }
    }

    private fun flyttLenkeTilNyDokumentHvisOriginalDokumentErSlettet(slettetDokumenter: List<Dokument>) {
        slettetDokumenter.forEach {
            val lenketDokumenter =
                dokumentTjeneste.hentDokumenterMedReferanse(it.dokumentreferanse).filter { l -> l.dokumentreferanse != it.dokumentreferanse }
            val erLenketAvAndreDokumenter = lenketDokumenter.isNotEmpty()
            if (erLenketAvAndreDokumenter) {
                flyttLenkeTilNyDokumentHvisOriginalDokumentErSlettet(lenketDokumenter, it)
            }

        }
    }

    private fun flyttLenkeTilNyDokumentHvisOriginalDokumentErSlettet(lenketDokumenter: List<Dokument>, slettetDokument: Dokument) {
        val nyOriginalDokument = lenketDokumenter[0].copy(
            dokumentreferanseOriginal = slettetDokument.dokumentreferanseOriginal,
            journalpostIdOriginal = slettetDokument.journalpostIdOriginal,
            arkivsystem = slettetDokument.arkivsystem
        )
        val oppdatertLenketDokumenter = lenketDokumenter.slice(1 until lenketDokumenter.size).map { lenketDokument ->
            lenketDokument.copy(
                dokumentreferanseOriginal = nyOriginalDokument.dokumentreferanse,
                journalpostIdOriginal = nyOriginalDokument.forsendelseId.toString(),
                arkivsystem = DokumentArkivSystem.FORSENDELSE
            )
        }
        dokumentTjeneste.lagreDokumenter(listOf(nyOriginalDokument) + oppdatertLenketDokumenter)
    }

    private fun OppdaterDokumentForespørsel.konverterTilOpprettDokumentForespørsel(): List<OpprettDokumentForespørsel> {
        return if (this.journalpostId?.erForsendelse == true) this.konverterTilOpprettDokumentForespørselMedOriginalLenketDokumenter()
        else listOf(this.tilOpprettDokumentForespørsel())
    }

    private fun OppdaterDokumentForespørsel.konverterTilOpprettDokumentForespørselMedOriginalLenketDokumenter(): List<OpprettDokumentForespørsel> {
        val dokumentForsendelse =
            this.journalpostId?.erForsendelse?.let { forsendelseTjeneste.medForsendelseId(this.journalpostId.numerisk) }
                ?: return listOf(this.tilOpprettDokumentForespørsel())

        return if (this.dokumentreferanse.isNullOrEmpty()) dokumentForsendelse.dokumenter.map { dok -> dok.opprettDokumentForespørselMedOriginalDokument() }
        else {
            val dokumentLenket = dokumentForsendelse.dokumenter.hentDokument(this.dokumentreferanse)!!
            listOf(dokumentLenket.opprettDokumentForespørselMedOriginalDokument())
        }

    }

    private fun Dokument.opprettDokumentForespørselMedOriginalDokument() = dokumentTjeneste.hentOriginalDokument(this).tilOpprettDokumentForespørsel()

    private fun Dokument.tilOpprettDokumentForespørsel() =
        OpprettDokumentForespørsel(
            tittel = tittel,
            dokumentreferanse = dokumentreferanse,
            status = this.tilDokumentStatusTo(),
            dokumentmalId = dokumentmalId,
            journalpostId = forsendelseId.toString(),
            dokumentDato = dokumentDato,
            arkivsystem = this.tilArkivSystemDto()
        )

    fun opphevFerdigstillingAvDokument(forsendelseId: Long, dokumentreferanse: String): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()

        log.info { "Opphever ferdigstilling av dokument $dokumentreferanse i forsendelse $forsendelseId" }

        val oppdatertForsendelse = forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = opphevFerdigstillDokument(forsendelse, dokumentreferanse)
            )
        )

        val oppdatertDokument = oppdatertForsendelse.dokumenter.hentDokument(dokumentreferanse)!!
        return DokumentRespons(
            dokumentreferanse = oppdatertDokument.dokumentreferanse,
            tittel = oppdatertDokument.tittel,
            dokumentDato = oppdatertDokument.dokumentDato,
            status = oppdatertDokument.tilDokumentStatusTo()
        )
    }

    fun oppdaterDokument(forsendelseId: Long, dokumentreferanse: String, forespørsel: OppdaterDokumentForespørsel): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()
        forespørsel.valider(forsendelse, dokumentreferanse)

        log.info { "Oppdaterer dokument $dokumentreferanse i forsendelse $forsendelseId" }

        val oppdatertForsendelse = forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = oppdaterDokument(forsendelse, dokumentreferanse, forespørsel)
            )
        )

        val oppdatertDokument = oppdatertForsendelse.dokumenter.hentDokument(dokumentreferanse)!!
        return DokumentRespons(
            dokumentreferanse = oppdatertDokument.dokumentreferanse,
            tittel = oppdatertDokument.tittel,
            dokumentDato = oppdatertDokument.dokumentDato,
            redigeringMetadata = oppdatertDokument.metadata.hentRedigeringmetadata()

        )
    }
//    private fun oppdaterMottaker(eksisterendeMottaker: Mottaker?, oppdatertMottaker: MottakerTo?): Mottaker?{
//        if (oppdatertMottaker == null) return eksisterendeMottaker
//        if (eksisterendeMottaker == null) return oppdatertMottaker.tilMottakerDo()
//
//        return eksisterendeMottaker.copy(
//            navn = oppdatertMottaker.navn ?: eksisterendeMottaker.navn,
//            ident = oppdatertMottaker.ident ?: eksisterendeMottaker.ident,
//            identType = oppdatertMottaker.tilIdentType(eksisterendeMottaker.identType),
//            adresse = oppdaterAdresse(eksisterendeMottaker.adresse, oppdatertMottaker.adresse)
//        )
//    }

//    private fun oppdaterAdresse(eksisterendeAdresse: Adresse? = null, oppdatertAdresse: MottakerAdresseTo?): Adresse?{
//        if (oppdatertAdresse == null) return eksisterendeAdresse
//        if (eksisterendeAdresse == null) return oppdatertAdresse.tilAdresseDo()
//        return oppdatertAdresse.tilAdresseDo().copy(id = eksisterendeAdresse.id)
//    }

    private fun oppdaterDokumenter(
        forsendelse: Forsendelse,
        forespørsel: OppdaterForsendelseForespørsel
    ): List<Dokument> {
        val oppdaterteDokumenter = forsendelse.dokumenter
            .mapIndexed { i, it ->
                val oppdaterDokument = forespørsel.hentDokument(it.dokumentreferanse)
                val indeks = forespørsel.dokumenter.indexOf(oppdaterDokument)
                it.copy(
                    tittel = oppdaterDokument?.tittel ?: it.tittel,
                    rekkefølgeIndeks = indeks,
                    dokumentDato = if (indeks == 0) forespørsel.dokumentDato ?: it.dokumentDato else it.dokumentDato
                )
            }

        return oppdaterteDokumenter.sortertEtterRekkefølge
    }


    fun ferdigstillDokument(forsendelseId: Long, dokumentreferanse: String): DokumentRespons {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId)
            ?: fantIkkeForsendelse(forsendelseId)
        forsendelse.validerKanEndreForsendelse()

        log.info { "Ferdgistiller dokument $dokumentreferanse i forsendelse $forsendelseId" }

        val oppdatertForsendelse = forsendelseTjeneste.lagre(
            forsendelse.copy(
                dokumenter = ferdigstillDokument(forsendelse, dokumentreferanse)
            )
        )

        val oppdatertDokument = oppdatertForsendelse.dokumenter.hentDokument(dokumentreferanse)!!
        return DokumentRespons(
            dokumentreferanse = oppdatertDokument.dokumentreferanse,
            tittel = oppdatertDokument.tittel,
            dokumentDato = oppdatertDokument.dokumentDato,
            status = oppdatertDokument.tilDokumentStatusTo()
        )
    }
}
