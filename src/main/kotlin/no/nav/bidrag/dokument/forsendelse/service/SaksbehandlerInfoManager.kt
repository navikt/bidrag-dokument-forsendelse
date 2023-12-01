package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.commons.security.SikkerhetsKontekst.erIApplikasjonKontekst
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler
import org.springframework.stereotype.Service

val FORSENDELSE_APP_ID = "bidrag-dokument-forsendelse"

@Service
class SaksbehandlerInfoManager {
    fun hentSaksbehandlerBrukerId(): String? = if (erIApplikasjonKontekst()) FORSENDELSE_APP_ID else TokenUtils.hentBruker()

    fun hentSaksbehandler(): Saksbehandler? {
        val saksbehandlerIdent = hentSaksbehandlerBrukerId() ?: return null
        return try {
            val saksbehandlerNavn = SaksbehandlernavnProvider.hentSaksbehandlernavn(saksbehandlerIdent)
            Saksbehandler(saksbehandlerIdent, saksbehandlerNavn ?: saksbehandlerIdent)
        } catch (e: Exception) {
            Saksbehandler(saksbehandlerIdent, saksbehandlerIdent)
        }
    }

    fun erApplikasjonBruker(): Boolean = TokenUtils.erApplikasjonsbruker()
}
