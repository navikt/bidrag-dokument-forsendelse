package no.nav.bidrag.dokument.forsendelse.service.dao

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.service.SaksbehandlerInfoManager
import no.nav.bidrag.dokument.forsendelse.service.TilgangskontrollService
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ForsendelseTjeneste(
    private val forsendelseRepository: ForsendelseRepository,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val tilgangskontrollService: TilgangskontrollService
) {

    fun hentAlleMedSaksnummer(saksnummer: String): List<Forsendelse> {
        tilgangskontrollService.sjekkTilgangSak(saksnummer)
        return forsendelseRepository.hentAlleMedSaksnummer(saksnummer)
    }

    fun medForsendelseId(forsendelseId: Long): Forsendelse? {
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId)
        forsendelse?.let { tilgangskontrollService.sjekkTilgangForsendelse(it) }
        return forsendelse
    }

    fun hentDistribuerteForsendelserUtenDistribusjonKanal(limit: Int): List<Forsendelse> {
        return forsendelseRepository.hentDistribuerteForsendelseUtenKanal(Pageable.ofSize(limit), LocalDateTime.now().minusHours(2))
    }

    fun hentFerdigstilteIkkeDistribuert(): List<Forsendelse> {
        return forsendelseRepository.hentFerdigstilteIkkeDistribuert()
    }


    fun lagre(forsendelse: Forsendelse): Forsendelse {
        val bruker = saksbehandlerInfoManager.hentSaksbehandler()
        return forsendelseRepository.save(
            forsendelse.copy(
                endretAvIdent = bruker?.ident ?: forsendelse.endretAvIdent,
                endretTidspunkt = LocalDateTime.now()
            )
        )
    }
}