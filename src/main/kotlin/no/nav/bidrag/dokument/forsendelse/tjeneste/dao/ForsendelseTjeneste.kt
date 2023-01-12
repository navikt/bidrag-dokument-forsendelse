package no.nav.bidrag.dokument.forsendelse.tjeneste.dao

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.tjeneste.SaksbehandlerInfoManager
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ForsendelseTjeneste(private val forsendelseRepository: ForsendelseRepository, private val saksbehandlerInfoManager: SaksbehandlerInfoManager) {

    fun hentAlleMedSaksnummer(saksnummer: String): List<Forsendelse> {
        return forsendelseRepository.hentAlleMedSaksnummer(saksnummer)
    }

    fun medForsendelseId(forsendelseId: Long): Forsendelse? {
        return forsendelseRepository.medForsendelseId(forsendelseId)
    }

    fun lagre(forsendelse: Forsendelse): Forsendelse {
        val bruker = saksbehandlerInfoManager.hentSaksbehandler()
        return forsendelseRepository.save(forsendelse.copy(
            endretAvIdent = bruker?.ident ?: forsendelse.endretAvIdent,
            endretTidspunkt = LocalDateTime.now()))
    }
}