package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.JournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.model.Dokumentreferanse
import no.nav.bidrag.dokument.forsendelse.model.FantIkkeDokument
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.hent
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilForsendelseRespons
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.tilJournalpostDto
import org.springframework.stereotype.Component

@Component
class HentDokumentTjeneste(val forsendelseTjeneste: ForsendelseTjeneste) {


    fun hentDokument(forsendelseId: Long, dokumentreferanse: Dokumentreferanse): ByteArray {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: throw FantIkkeDokument("Fant ikke forsendelse med forsendelseId=$forsendelseId")

        val arkivSystem = forsendelse.dokumenter.hent(dokumentreferanse)?.arkivsystem
        if (arkivSystem == null || arkivSystem != DokumentArkivSystem.BIDRAG){
            throw FantIkkeDokument("Kan ikke hente dokument $dokumentreferanse med forsendelseId $forsendelseId fra arkivsystem = $arkivSystem")
        }

        val dokument = forsendelse.dokumenter.hent(dokumentreferanse)
        // TODO: Hent dokument fra ny løsning
        // TODO: Hent redigert dokument fra bytearray
        return "DOK".toByteArray()
    }


}