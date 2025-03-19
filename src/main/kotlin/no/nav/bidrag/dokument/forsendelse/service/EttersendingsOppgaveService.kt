package no.nav.bidrag.dokument.forsendelse.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import no.nav.bidrag.commons.service.hentNavSkjemaKodeverk
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterEttersendingsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettEttersendingsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.SlettEttersendingsoppgave
import no.nav.bidrag.dokument.forsendelse.api.dto.SlettEttersendingsoppgaveVedleggRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.InnsendingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentSoknadDto
import no.nav.bidrag.dokument.forsendelse.consumer.dto.HentEtterseningsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.fjernKontrollTegn
import no.nav.bidrag.dokument.forsendelse.model.ugyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Ettersendingsoppgave
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.EttersendingsoppgaveVedlegg
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.valider
import no.nav.bidrag.transport.dokument.DokumentType
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class EttersendingsOppgaveService(
    val dokumentConsumer: BidragDokumentConsumer,
    val forsendelseService: ForsendelseTjeneste,
    val innsendingConsumer: InnsendingConsumer,
) {
    fun hentEksisterendeEttersendingsoppgaverForBruker(forsendelseId: Long): Map<String, List<DokumentSoknadDto>> {
        val forsendelse = forsendelseService.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        val journal = dokumentConsumer.hentJournal(forsendelse.saksnummer)
        val navskjema = hentNavSkjemaKodeverk()
        val inngåendeJournalposter =
            journal
                .filter {
                    it.gjelderAktor?.ident == forsendelse.gjelderIdent
                }.filter { it.dokumentType == DokumentType.INNGÅENDE }
                .filter { navskjema.containsKey(it.brevkode?.kode) }
        val skjemaIder = inngåendeJournalposter.map { it.brevkode!!.kode!! }
        val ettersendingsoppgaver = hentEttersendingsoppgaver(forsendelseId, skjemaIder)
        return ettersendingsoppgaver
    }

    fun hentEttersendingsoppgaver(
        forsendelseId: Long,
        skjemaIder: List<String>,
    ): Map<String, List<DokumentSoknadDto>> {
        val forsendelse = forsendelseService.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)

        return skjemaIder
            .associateWith {
                try {
                    innsendingConsumer.hentEttersendingsoppgave(
                        HentEtterseningsoppgaveRequest(
                            brukerId = forsendelse.gjelderIdent,
                            skjemanr = it,
                        ),
                    )
                } catch (e: Exception) {
                    log.error(e) { "Feil ved henting av ettersendingsoppgaver" }
                    emptyList()
                }
            }
    }

    @Transactional
    fun opprettEttersendingsoppgave(request: OpprettEttersendingsoppgaveRequest) {
        log.info { "Oppretter ettersendingsoppgave for forsendelse ${request.forsendelseId}" }
        secureLogger.info { "Oppretter ettersendingsoppgave $request" }
        val forsendelse = forsendelseService.medForsendelseId(request.forsendelseId) ?: fantIkkeForsendelse(request.forsendelseId)
        if (forsendelse.gjelderIdent != forsendelse.mottaker?.ident) {
            ugyldigForespørsel("Kan ikke opprette ettersendingsoppgave hvis gjelder er ulik mottaker")
        }
        if (forsendelse.ettersendingsoppgave != null) {
            ugyldigForespørsel("Forsendelse ${request.forsendelseId} har allerede en ettersendingsoppgave")
        }
        forsendelse.ettersendingsoppgave =
            Ettersendingsoppgave(
                forsendelse = forsendelse,
                tittel = request.tittel?.fjernKontrollTegn(),
                ettersendelseForJournalpostId = request.ettersendelseForJournalpostId,
                skjemaId = request.skjemaId,
            )
    }

    @Transactional
    fun oppdaterEttersendingsoppgave(request: OppdaterEttersendingsoppgaveRequest) {
        log.info { "Oppdaterer ettersendingsoppgave for forsendelse ${request.forsendelseId}" }
        secureLogger.info { "Oppdaterer ettersendingsoppgave $request" }
        val forsendelse = forsendelseService.medForsendelseId(request.forsendelseId) ?: fantIkkeForsendelse(request.forsendelseId)
        request.valider(forsendelse)
        val varselEttersendelse =
            forsendelse.ettersendingsoppgave
                ?: ugyldigForespørsel("Fant ikke ettersendingsoppgave i forsendelse ${request.forsendelseId}")

        if (request.oppdaterDokument != null) {
            val oppdaterDokument =
                if (request.oppdaterDokument.id ==
                    null
                ) {
                    val nyDokument = EttersendingsoppgaveVedlegg(ettersendingsoppgave = varselEttersendelse)
                    varselEttersendelse.vedleggsliste.add(nyDokument)
                    nyDokument
                } else {
                    varselEttersendelse.vedleggsliste.find { it.id == request.oppdaterDokument.id }
                        ?: ugyldigForespørsel("Fant ikke ettersendingsoppgave vedlegg med id ${request.oppdaterDokument.id}")
                }

            oppdaterDokument.tittel = request.oppdaterDokument.tittel.fjernKontrollTegn()
            oppdaterDokument.skjemaId = request.oppdaterDokument.skjemaId
        }

        request.tittel?.let {
            varselEttersendelse.tittel = it.fjernKontrollTegn()
        }
        request.innsendingsfristDager?.let {
            varselEttersendelse.innsendingsfristDager = it
        }
        request.ettersendelseForJournalpostId?.let {
            varselEttersendelse.ettersendelseForJournalpostId = it
            varselEttersendelse.skjemaId = request.skjemaId
        }
    }

    @Transactional
    fun slettEttersendingsoppgaveVedlegg(request: SlettEttersendingsoppgaveVedleggRequest) {
        val forsendelse = forsendelseService.medForsendelseId(request.forsendelseId) ?: fantIkkeForsendelse(request.forsendelseId)
        val varselEttersendelse =
            forsendelse.ettersendingsoppgave
                ?: ugyldigForespørsel("Fant ikke varsel ettersendelse i forsendelse ${request.forsendelseId}")

        val slettDokument =
            varselEttersendelse.vedleggsliste.find { it.id == request.id }
                ?: ugyldigForespørsel("Fant ikke varsel ettersendelse dokument med id ${request.id}")

        varselEttersendelse.vedleggsliste.remove(slettDokument)
    }

    @Transactional
    fun slettEttersendingsoppave(request: SlettEttersendingsoppgave) {
        log.info { "Sletter ettersendingsoppgave for forsendelse ${request.forsendelseId}" }
        val forsendelse = forsendelseService.medForsendelseId(request.forsendelseId) ?: fantIkkeForsendelse(request.forsendelseId)

        forsendelse.ettersendingsoppgave = null
    }
}
