package no.nav.bidrag.dokument.forsendelse.service

import jakarta.transaction.Transactional
import mu.KotlinLogging
import no.nav.bidrag.dokument.forsendelse.model.UgyldigAvvikForForsendelse
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.ugyldigAvviksForespørsel
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanEndreForsendelse
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanEndreTilFagområde
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentFagområde
import no.nav.bidrag.transport.dokument.AvvikType
import no.nav.bidrag.transport.dokument.Avvikshendelse
import no.nav.bidrag.transport.dokument.Fagomrade
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Component
@Transactional
class AvvikService(
    private val forsendelseTjeneste: ForsendelseTjeneste,
    private val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    private val hendelseBestillingService: ForsendelseHendelseBestillingService,
) {
    fun hentAvvik(forsendelseId: Long): List<AvvikType> {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)

        return if (forsendelse.status == ForsendelseStatus.UNDER_PRODUKSJON) {
            listOf(
                AvvikType.FEILFORE_SAK,
                AvvikType.SLETT_JOURNALPOST,
                AvvikType.ENDRE_FAGOMRADE,
            )
        } else if (forsendelse.status == ForsendelseStatus.UNDER_OPPRETTELSE) {
            listOf(AvvikType.SLETT_JOURNALPOST)
        } else {
            emptyList()
        }
    }

    fun utførAvvik(
        forsendelseId: Long,
        avvikshendelse: Avvikshendelse,
        enhet: String?,
    ) {
        forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        val avvikType = AvvikType.valueOf(avvikshendelse.avvikType)
        if (!isValidAvvikForForsendelse(
                forsendelseId,
                avvikType,
            )
        ) {
            throw UgyldigAvvikForForsendelse("Kan ikke utføre avvik $avvikType på forsendelse $forsendelseId")
        }
        when (avvikType) {
            AvvikType.FEILFORE_SAK -> avbrytForsendelse(forsendelseId)
            AvvikType.SLETT_JOURNALPOST -> slettForsendelse(forsendelseId)
            AvvikType.ENDRE_FAGOMRADE -> endreFagområde(forsendelseId, avvikshendelse)
            else -> {}
        }
        log.info { "Utførte avvik $avvikType for forsendelseId $forsendelseId og enhet $enhet" }
    }

    private fun isValidAvvikForForsendelse(
        forsendelseId: Long,
        avvikType: AvvikType,
    ): Boolean {
        return hentAvvik(forsendelseId).contains(avvikType)
    }

    private fun endreFagområde(
        forsendelseId: Long,
        avvikshendelse: Avvikshendelse,
    ): Boolean {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return false
        val endreTilFagområde = avvikshendelse.hentFagområde() ?: ugyldigAvviksForespørsel("forespørsel mangler fagområde")
        forsendelse.validerKanEndreForsendelse()
        forsendelse.validerKanEndreTilFagområde(endreTilFagområde)
        forsendelseTjeneste.lagre(
            forsendelse.copy(
                tema =
                    when (endreTilFagområde) {
                        Fagomrade.FARSKAP -> ForsendelseTema.FAR
                        else -> ForsendelseTema.BID
                    },
            ),
        )

        return true
    }

    private fun slettForsendelse(forsendelseId: Long): Boolean {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return false
        forsendelse.validerKanEndreForsendelse()
        val saksbehandler = saksbehandlerInfoManager.hentSaksbehandler()
        forsendelseTjeneste.lagre(
            forsendelse.copy(
                status = ForsendelseStatus.SLETTET,
                avbruttAvIdent = saksbehandler?.ident,
                avbruttTidspunkt = LocalDateTime.now(),
            ),
        )

        hendelseBestillingService.bestill(forsendelseId)
        return true
    }

    private fun avbrytForsendelse(forsendelseId: Long): Boolean {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return false
        forsendelse.validerKanEndreForsendelse()
        val saksbehandler = saksbehandlerInfoManager.hentSaksbehandler()
        forsendelseTjeneste.lagre(
            forsendelse.copy(
                status = ForsendelseStatus.AVBRUTT,
                avbruttAvIdent = saksbehandler?.ident,
                avbruttTidspunkt = LocalDateTime.now(),
            ),
        )
        hendelseBestillingService.bestill(forsendelseId)
        return true
    }
}
