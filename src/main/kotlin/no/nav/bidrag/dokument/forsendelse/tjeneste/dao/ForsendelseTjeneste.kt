package no.nav.bidrag.dokument.forsendelse.tjeneste.dao

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.tjeneste.SaksbehandlerInfoManager
import no.nav.bidrag.dokument.forsendelse.tjeneste.TilgangskontrollTjeneste
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ForsendelseTjeneste(private val forsendelseRepository: ForsendelseRepository, private val saksbehandlerInfoManager: SaksbehandlerInfoManager, private val tilgangskontrollTjeneste: TilgangskontrollTjeneste) {

    fun hentAlleMedSaksnummer(saksnummer: String): List<Forsendelse> {
        tilgangskontrollTjeneste.sjekkTilgangSak(saksnummer)
        return forsendelseRepository.hentAlleMedSaksnummer(saksnummer)
    }

    fun medForsendelseId(forsendelseId: Long): Forsendelse? {
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId)
        forsendelse?.let { tilgangskontrollTjeneste.sjekkTilgangForsendelse(it)  }
        return forsendelse
    }

    fun lagre(forsendelse: Forsendelse): Forsendelse {
        val bruker = saksbehandlerInfoManager.hentSaksbehandler()
        return forsendelseRepository.save(forsendelse.copy(
            endretAvIdent = bruker?.ident ?: forsendelse.endretAvIdent,
            endretTidspunkt = LocalDateTime.now()))
    }
}