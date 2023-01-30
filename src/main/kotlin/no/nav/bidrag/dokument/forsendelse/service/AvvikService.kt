package no.nav.bidrag.dokument.forsendelse.service

import mu.KotlinLogging
import no.nav.bidrag.dokument.dto.AvvikType
import no.nav.bidrag.dokument.dto.Avvikshendelse
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.model.UgyldigAvvikForForsendelse
import no.nav.bidrag.dokument.forsendelse.model.fantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.service.validering.ForespørselValidering.validerKanEndreForsendelse
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import javax.transaction.Transactional
private val log = KotlinLogging.logger {}

@Component
@Transactional
class AvvikService(private val forsendelseTjeneste: ForsendelseTjeneste, private val saksbehandlerInfoManager: SaksbehandlerInfoManager) {

    fun hentAvvik(forsendelseId: Long): List<AvvikType> {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)

        return if (forsendelse.status == ForsendelseStatus.UNDER_PRODUKSJON) listOf(AvvikType.FEILFORE_SAK)
        else emptyList()
    }

    fun utførAvvik(forsendelseId: Long, avvikshendelse: Avvikshendelse, enhet: String?) {
        forsendelseTjeneste.medForsendelseId(forsendelseId) ?: fantIkkeForsendelse(forsendelseId)
        val avvikType = AvvikType.valueOf(avvikshendelse.avvikType)
        if (!isValidAvvikForForsendelse(forsendelseId, avvikType)) throw UgyldigAvvikForForsendelse("Kan ikke utføre avvik $avvikType på forsendelse $forsendelseId")
        when(avvikType){
            AvvikType.FEILFORE_SAK -> avbrytForsendelse(forsendelseId)
            else -> {}
        }
        log.info { "Utførte avvik $avvikType for forsendelseId $forsendelseId og enhet $enhet"}
    }

    private fun isValidAvvikForForsendelse(forsendelseId: Long, avvikType: AvvikType): Boolean {
        return hentAvvik(forsendelseId).contains(avvikType)
    }
    private fun avbrytForsendelse(forsendelseId: Long): Boolean {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return false
        forsendelse.validerKanEndreForsendelse()
        val saksbehandler = saksbehandlerInfoManager.hentSaksbehandler()
        forsendelseTjeneste.lagre(forsendelse.copy(
            status = ForsendelseStatus.AVBRUTT,
            avbruttAvIdent = saksbehandler?.ident,
            avbruttTidspunkt = LocalDateTime.now(),
            endretTidspunkt = LocalDateTime.now(),
            endretAvIdent = saksbehandler?.ident ?: forsendelse.endretAvIdent
        ))

        return true
    }
}