package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.commons.security.SikkerhetsKontekst.Companion.erIApplikasjonKontekst
import no.nav.bidrag.commons.security.service.OidcTokenManager
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragOrganisasjonKonsumer
import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler
import org.springframework.stereotype.Service

@Service
class SaksbehandlerInfoManager(
    private val bidragOrganisasjonKonsumer: BidragOrganisasjonKonsumer,
    private val oidcTokenManager: OidcTokenManager
) {
    fun hentSaksbehandlerBrukerId(): String? = if (erIApplikasjonKontekst()) "bidrag-dokument-forsendelse" else oidcTokenManager.hentSaksbehandlerIdentFraToken()

    fun hentSaksbehandler(): Saksbehandler? {
        val saksbehandlerIdent = hentSaksbehandlerBrukerId() ?: return null
        return try {
            val saksbehandlerNavn = bidragOrganisasjonKonsumer.hentSaksbehandlerInfo(saksbehandlerIdent)?.navn
            Saksbehandler(saksbehandlerIdent, saksbehandlerNavn)
        } catch (e: Exception) {
            Saksbehandler(saksbehandlerIdent, saksbehandlerIdent)
        }
    }

    fun erApplikasjonBruker(): Boolean = oidcTokenManager.erApplikasjonBruker()
}