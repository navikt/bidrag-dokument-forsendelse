package no.nav.bidrag.dokument.forsendelse.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import no.nav.bidrag.commons.service.hentNavSkjemaKodeverk
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterEttersendingsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettEttersendingsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.SlettEttersendingsoppgave
import no.nav.bidrag.dokument.forsendelse.api.dto.SlettEttersendingsoppgaveVedleggRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.InnsendingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.Brukernotifikasjonstype
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentSoknadDto
import no.nav.bidrag.dokument.forsendelse.consumer.dto.EksternEttersendingsOppgave
import no.nav.bidrag.dokument.forsendelse.consumer.dto.HentEtterseningsoppgaveRequest
import no.nav.bidrag.dokument.forsendelse.consumer.dto.InnsendtVedleggDto
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.fjernKontrollTegn
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Ettersendingsoppgave
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.EttersendingsoppgaveVedlegg
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
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
                innsendingConsumer.hentEttersendingsoppgave(
                    HentEtterseningsoppgaveRequest(
                        brukerId = forsendelse.gjelderIdent,
                        skjemanr = it,
                    ),
                )
            }
    }

    fun sendVarsel(forsendelse: Forsendelse) {
        val varselEttersendelse =
            forsendelse.ettersendingsoppgave ?: run {
                log.warn { "Forsendelse ${forsendelse.forsendelseId} har ingen varseloppgave" }
                return
            }
        innsendingConsumer.opprettEttersendingsoppgave(
            EksternEttersendingsOppgave(
                brukerId = forsendelse.gjelderIdent,
                skjemanr = varselEttersendelse.skjemaId!!,
                sprak = forsendelse.språk,
                tittel = varselEttersendelse.tittel,
                tema = forsendelse.tema.name,
                brukernotifikasjonstype = Brukernotifikasjonstype.oppgave,
                vedleggsListe =
                    varselEttersendelse.vedleggsliste.map {
                        InnsendtVedleggDto(
                            vedleggsnr = it.skjemaId!!,
                            tittel = it.tittel,
                        )
                    },
            ),
        )
    }

    @Transactional
    fun opprettVarselEttersendelse(request: OpprettEttersendingsoppgaveRequest) {
        val forsendelse = forsendelseService.medForsendelseId(request.forsendelseId) ?: fantIkkeForsendelse(request.forsendelseId)
        forsendelse.ettersendingsoppgave =
            Ettersendingsoppgave(
                forsendelse = forsendelse,
                tittel = request.tittel.fjernKontrollTegn(),
                ettersendelseForJournalpostId = request.ettersendelseForJournalpostId,
                skjemaId = request.skjemaId,
            )
    }

    @Transactional
    fun oppdaterVarselEttersendelse(request: OppdaterEttersendingsoppgaveRequest) {
        request.valider()
        val forsendelse = forsendelseService.medForsendelseId(request.forsendelseId) ?: fantIkkeForsendelse(request.forsendelseId)
        val varselEttersendelse =
            forsendelse.ettersendingsoppgave
                ?: throw IllegalArgumentException("Fant ikke varsel ettersendelse i forsendelse ${request.forsendelseId}")

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
                        ?: throw IllegalArgumentException("Fant ikke varsel ettersendelse dokument med id ${request.oppdaterDokument.id}")
                }

            oppdaterDokument.tittel = request.oppdaterDokument.tittel.fjernKontrollTegn()
            oppdaterDokument.skjemaId = request.oppdaterDokument.skjemaId
        }

        if (request.tittel != null) {
            varselEttersendelse.tittel = request.tittel.fjernKontrollTegn()
        }

        if (request.innsendingsfristDager != null) {
            varselEttersendelse.innsendingsfristDager = request.innsendingsfristDager
        }
        if (request.ettersendelseForJournalpostId != null) {
            varselEttersendelse.ettersendelseForJournalpostId = request.ettersendelseForJournalpostId
            varselEttersendelse.skjemaId = request.skjemaId
        }
    }

    @Transactional
    fun slettVarselEttersendelseDokument(request: SlettEttersendingsoppgaveVedleggRequest) {
        val forsendelse = forsendelseService.medForsendelseId(request.forsendelseId) ?: fantIkkeForsendelse(request.forsendelseId)
        val varselEttersendelse =
            forsendelse.ettersendingsoppgave
                ?: throw IllegalArgumentException("Fant ikke varsel ettersendelse i forsendelse ${request.forsendelseId}")

        val slettDokument =
            varselEttersendelse.vedleggsliste.find { it.id == request.id }
                ?: throw IllegalArgumentException("Fant ikke varsel ettersendelse dokument med id ${request.id}")

        varselEttersendelse.vedleggsliste.remove(slettDokument)
    }

    @Transactional
    fun slettEttersendingsoppave(request: SlettEttersendingsoppgave) {
        val forsendelse = forsendelseService.medForsendelseId(request.forsendelseId) ?: fantIkkeForsendelse(request.forsendelseId)

        forsendelse.ettersendingsoppgave = null
    }
}
