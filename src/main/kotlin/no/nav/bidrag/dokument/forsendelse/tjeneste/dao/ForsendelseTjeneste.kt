package no.nav.bidrag.dokument.forsendelse.tjeneste.dao

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import org.springframework.stereotype.Component

@Component
class ForsendelseTjeneste(private val forsendelseRepository: ForsendelseRepository) {

    fun hentAlleMedSaksnummer(saksnummer: String): List<Forsendelse> {
        return forsendelseRepository.hentAlleMedSaksnummer(saksnummer)
    }

    fun medForsendelseId(forsendelseId: Long): Forsendelse? {
        return forsendelseRepository.medForsendelseId(forsendelseId)
    }

    fun lagre(forsendelse: Forsendelse): Forsendelse {
       return forsendelseRepository.save(forsendelse)
    }
}