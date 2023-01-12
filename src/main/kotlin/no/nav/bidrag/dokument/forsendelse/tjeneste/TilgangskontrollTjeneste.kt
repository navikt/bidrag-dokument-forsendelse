package no.nav.bidrag.dokument.forsendelse.tjeneste

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.konsumenter.BidragSakKonsumer
import no.nav.bidrag.dokument.forsendelse.model.ingenTilgang
import org.springframework.stereotype.Service

@Service
class TilgangskontrollTjeneste(private val bidragSakKonsumer: BidragSakKonsumer) {


    fun sjekkTilgangSak(saksnummer: String){
        if (!bidragSakKonsumer.sjekkTilgangSak(saksnummer)) ingenTilgang("Ingen tilgang til saksnummer $saksnummer")
    }

    fun sjekkTilgangForsendelse(forsendelse: Forsendelse){
        sjekkTilgangSak(forsendelse.saksnummer)
        sjekkTilgangPerson(forsendelse.gjelderIdent)
    }

    fun sjekkTilgangPerson(personnummer: String){
        bidragSakKonsumer.sjekkTilgangPerson(personnummer)
    }
}