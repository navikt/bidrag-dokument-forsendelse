package no.nav.bidrag.dokument.forsendelse.api.dto

data class OpprettEttersendingsoppgaveRequest(
    val forsendelseId: Long,
    val tittel: String?,
    val ettersendelseForJournalpostId: String,
    val skjemaId: String,
)

data class OppdaterEttersendingsoppgaveRequest(
    val forsendelseId: Long,
    val tittel: String? = null,
    val ettersendelseForJournalpostId: String? = null,
    val skjemaId: String? = null,
    val innsendingsfristDager: Int? = null,
    val oppdaterDokument: OppdaterEttersendelseDokumentRequest? = null,
)

data class SlettEttersendingsoppgave(
    val forsendelseId: Long,
)

data class SlettEttersendingsoppgaveVedleggRequest(
    val forsendelseId: Long,
    val id: Long,
)

data class OppdaterEttersendelseDokumentRequest(
    val id: Long? = null,
    val tittel: String,
    val skjemaId: String? = null,
)

data class HentEttersendingsoppgaverRequest(
    val forsendelseId: String,
    val skjemaIder: List<String>,
)
