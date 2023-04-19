package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.commons.security.SikkerhetsKontekst.erIApplikasjonKontekst
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.dokument.forsendelse.consumer.BidragOrganisasjonConsumer
import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler
import org.springframework.stereotype.Service

@Service
class SaksbehandlerInfoManager(private val bidragOrganisasjonConsumer: BidragOrganisasjonConsumer) {
    fun hentSaksbehandlerBrukerId(): String? = if (erIApplikasjonKontekst()) "bidrag-dokument-forsendelse" else TokenUtils.hentBruker()

    fun hentSaksbehandler(): Saksbehandler? {
        val saksbehandlerIdent = hentSaksbehandlerBrukerId() ?: return null
        return try {
            val saksbehandlerNavn = bidragOrganisasjonConsumer.hentSaksbehandlerInfo(saksbehandlerIdent)?.navn
            Saksbehandler(saksbehandlerIdent, saksbehandlerNavn?.verdi)
        } catch (e: Exception) {
            Saksbehandler(saksbehandlerIdent, saksbehandlerIdent)
        }
    }

    fun erApplikasjonBruker(): Boolean = TokenUtils.erApplikasjonsbruker()
}
