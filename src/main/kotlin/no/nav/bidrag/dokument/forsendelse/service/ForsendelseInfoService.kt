package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.DokumentDto
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.persistence.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.persistence.model.erAlleDokumenterFerdigstilt
import no.nav.bidrag.dokument.forsendelse.persistence.model.hoveddokument
import no.nav.bidrag.dokument.forsendelse.persistence.model.hoveddokumentFørst
import no.nav.bidrag.dokument.forsendelse.persistence.repository.ForsendelseRepository
import org.springframework.stereotype.Component
import java.time.LocalDate
import kotlin.jvm.optionals.toList

fun Forsendelse.tilJournalpostDto() = JournalpostDto(
    avsenderMottaker = this.mottaker?.let {
        AvsenderMottakerDto(it.navn, it.ident)
    },
    innhold = this.hoveddokument?.tittel,
    fagomrade = "BID",
    dokumentType = when(this.forsendelseType){
        ForsendelseType.NOTAT -> "X"
        ForsendelseType.UTGÅENDE -> "U"
    },
    journalstatus = if (this.erAlleDokumenterFerdigstilt) "J" else "D",
    journalpostId = "BIF_${this.forsendelseId}",
    dokumentDato = this.opprettetTidspunkt.toLocalDate(),
    journalfortDato = this.opprettetTidspunkt.toLocalDate(),
    journalforendeEnhet = this.enhet,
    dokumenter = this.hoveddokumentFørst.map {
        DokumentDto(
            dokumentreferanse = it.dokumentreferanse,
            tittel = it.tittel
        )
    })
@Component
class ForsendelseInfoService(val forsendelseRepository: ForsendelseRepository) {


    fun hentForsendelseForSak(saksnummer: String): List<JournalpostDto> {
        val forsendelser = forsendelseRepository.hentAlleMedSaksnummer(saksnummer)

        return forsendelser.map { forsendelse -> forsendelse.tilJournalpostDto() }
    }

    fun hentForsendelse(forsendelseId: Long): JournalpostResponse? {
        val forsendelseOpt = forsendelseRepository.findById(forsendelseId)
        if (forsendelseOpt.isEmpty){
            return null
        }

        val forsendelse = forsendelseOpt.get()

        return JournalpostResponse(
            journalpost = forsendelse.tilJournalpostDto(),
            sakstilknytninger = listOf(forsendelse.saksnummer)
        )
    }

}