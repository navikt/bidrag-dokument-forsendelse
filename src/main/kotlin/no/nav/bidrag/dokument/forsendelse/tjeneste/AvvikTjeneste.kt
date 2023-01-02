package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.dto.AvvikType
import no.nav.bidrag.dokument.dto.Avvikshendelse
import no.nav.bidrag.dokument.forsendelse.SIKKER_LOGG
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.model.FantIkkeForsendelse
import no.nav.bidrag.dokument.forsendelse.model.UgyldigAvvikForForsendelse
import no.nav.bidrag.dokument.forsendelse.tjeneste.dao.ForsendelseTjeneste
import no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser.validerKanEndreForsendelse
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import javax.transaction.Transactional

@Component
@Transactional
class AvvikTjeneste(private val forsendelseTjeneste: ForsendelseTjeneste, private val saksbehandlerInfoManager: SaksbehandlerInfoManager) {

    fun hentAvvik(forsendelseId: Long): List<AvvikType> {
        forsendelseTjeneste.medForsendelseId(forsendelseId) ?: FantIkkeForsendelse(forsendelseId)

        return listOf(AvvikType.FEILFORE_SAK)

    }

    fun utførAvvik(forsendelseId: Long, avvikshendelse: Avvikshendelse, enhet: String) {
        forsendelseTjeneste.medForsendelseId(forsendelseId) ?: FantIkkeForsendelse(forsendelseId)
        val avvikType = AvvikType.valueOf(avvikshendelse.avvikType)
        if (!isValidAvvikForForsendelse(forsendelseId, avvikType)) throw UgyldigAvvikForForsendelse("Kan ikke utføre avvik $avvikType på forsendelse $forsendelseId")
        when(avvikType){
            AvvikType.FEILFORE_SAK -> avbrytForsendelse(forsendelseId)
            else -> {}
        }
        SIKKER_LOGG.info("Utførte avvik $avvikType for forsendelseId $forsendelseId og enhet $enhet")
    }

    private fun isValidAvvikForForsendelse(forsendelseId: Long, avvikType: AvvikType): Boolean {
        return hentAvvik(forsendelseId).contains(avvikType)
    }
    private fun avbrytForsendelse(forsendelseId: Long): Boolean {
        val forsendelse = forsendelseTjeneste.medForsendelseId(forsendelseId) ?: return false
        forsendelse.validerKanEndreForsendelse()
        forsendelseTjeneste.lagre(forsendelse.copy(
            status = ForsendelseStatus.AVBRUTT,
            avbruttAvIdent = saksbehandlerInfoManager.hentSaksbehandlerBrukerId(),
            avbruttTidspunkt = LocalDateTime.now()
        ))

        return true
    }
}