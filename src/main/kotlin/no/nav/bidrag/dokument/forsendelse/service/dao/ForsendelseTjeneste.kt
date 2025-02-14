package no.nav.bidrag.dokument.forsendelse.service.dao

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.service.SaksbehandlerInfoManager
import no.nav.bidrag.dokument.forsendelse.service.TilgangskontrollService
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Component
class ForsendelseTjeneste(
    private val forsendelseRepository: ForsendelseRepository,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val tilgangskontrollService: TilgangskontrollService,
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

    fun hentForsendelserHvorEttersendingIkkeOpprettet(): List<Forsendelse> = forsendelseRepository.hentEttersendingerSomIkkeErOpprettet()

    fun hentDistribuerteForsendelserUtenDistribusjonKanal(limit: Int): List<Forsendelse> =
        forsendelseRepository.hentDistribuerteForsendelseUtenKanal(Pageable.ofSize(limit), LocalDateTime.now().minusHours(2))

    fun hentDistribuerteForsendelserDistribuertTilNavNo(
        limit: Int,
        afterDateInput: LocalDateTime?,
        beforeDateInput: LocalDateTime?,
        sjekketNavNoRedistribusjonTilSentralPrint: Boolean = false,
    ): List<Forsendelse> {
        val beforeDate = beforeDateInput ?: LocalDateTime.now().minusDays(4)
        val afterDate = afterDateInput ?: LocalDateTime.now().minusDays(200)
        LOGGER.info { "Henter distribuerte forsendelser med kanal NAV_NO fra $afterDate til $beforeDate" }
        return forsendelseRepository.hentDistribuerteForsendelseTilNAVNO(
            Pageable.ofSize(limit),
            beforeDate,
            afterDate,
            sjekketNavNoRedistribusjonTilSentralPrint.toString(),
        )
    }

    fun hentFerdigstilteIkkeDistribuert(): List<Forsendelse> = forsendelseRepository.hentFerdigstilteArkivertIJoarkIkkeDistribuert()

    fun hentForsendelserOpprettetFørDagensDatoIkkeDistribuert(): List<Forsendelse> {
        val forsendelser = forsendelseRepository.hentUnderProduksjonOpprettetFørDagensDato()
        return forsendelser.filter { it.dokumenter.erAlleFerdigstilt }
    }

    fun lagre(forsendelse: Forsendelse): Forsendelse {
        val bruker = saksbehandlerInfoManager.hentSaksbehandler()
        return forsendelseRepository.save(
            forsendelse.copy(
                endretAvIdent = bruker?.ident ?: forsendelse.endretAvIdent,
                endretTidspunkt = LocalDateTime.now(),
            ),
        )
    }
}
