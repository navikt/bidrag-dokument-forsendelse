package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragTIlgangskontrollKonsumer
import no.nav.bidrag.dokument.forsendelse.model.ingenTilgang
import org.springframework.stereotype.Service

@Service
class TilgangskontrollTjeneste(private val bidragTIlgangskontrollKonsumer: BidragTIlgangskontrollKonsumer) {


    fun sjekkTilgangSak(saksnummer: String){
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return
        if (!bidragTIlgangskontrollKonsumer.sjekkTilgangSak(saksnummer)) ingenTilgang("Ingen tilgang til saksnummer $saksnummer")
    }

    fun sjekkTilgangForsendelse(forsendelse: Forsendelse){
        sjekkTilgangSak(forsendelse.saksnummer)
        sjekkTilgangPerson(forsendelse.gjelderIdent)
    }

    fun sjekkTilgangPerson(personnummer: String){
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return
        bidragTIlgangskontrollKonsumer.sjekkTilgangPerson(personnummer)
    }
}