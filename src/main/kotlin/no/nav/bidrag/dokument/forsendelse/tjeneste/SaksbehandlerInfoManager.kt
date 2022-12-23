package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.commons.security.service.OidcTokenManager
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragOrganisasjonKonsumer
import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler
import org.springframework.stereotype.Service

@Service
class SaksbehandlerInfoManager(
    private val bidragOrganisasjonKonsumer: BidragOrganisasjonKonsumer,
    private val oidcTokenManager: OidcTokenManager
) {
    fun hentSaksbehandlerBrukerId(): String? = oidcTokenManager.hentSaksbehandlerIdentFraToken()

    fun hentSaksbehandler(): Saksbehandler? {
        return try {
            val saksbehandlerIdent = hentSaksbehandlerBrukerId() ?: return null
            val saksbehandlerNavn = bidragOrganisasjonKonsumer.hentSaksbehandlerInfo(saksbehandlerIdent)?.navn
            Saksbehandler(saksbehandlerIdent, saksbehandlerNavn)
        } catch (e: Exception) {
            null
        }
    }

    fun erApplikasjonBruker(): Boolean = oidcTokenManager.erApplikasjonBruker()
}