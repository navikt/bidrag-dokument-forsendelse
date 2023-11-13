package no.nav.bidrag.dokument.forsendelse.service.dao

import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.service.SaksbehandlerInfoManager
import no.nav.bidrag.dokument.forsendelse.service.TilgangskontrollService
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
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

    fun hentDistribuerteForsendelserDistribuertTilNavNo(limit: Int, afterDate: LocalDateTime?, beforeDate: LocalDateTime?): List<Forsendelse> {
        return forsendelseRepository.hentDistribuerteForsendelseTilNAVNO(
            Pageable.ofSize(limit),
            beforeDate ?: LocalDateTime.now().minusDays(2),
            afterDate ?: LocalDateTime.now().minusDays(100)
        )
    }

    fun hentFerdigstilteIkkeDistribuert(): List<Forsendelse> {
        return forsendelseRepository.hentFerdigstilteArkivertIJoarkIkkeDistribuert()
    }

    fun hentForsendelserOpprettetFørDagensDatoIkkeDistribuert(): List<Forsendelse> {
        val forsendelser = forsendelseRepository.hentUnderProduksjonOpprettetFørDagensDato()
        return forsendelser.filter { it.dokumenter.erAlleFerdigstilt }
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
