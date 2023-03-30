package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.dokument.forsendelse.consumer.BidragTIlgangskontrollConsumer
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.model.ingenTilgang
import org.springframework.stereotype.Service

@Service
class TilgangskontrollService(private val bidragTIlgangskontrollConsumer: BidragTIlgangskontrollConsumer) {

    fun sjekkTilgangSak(saksnummer: String) {
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return
        if (!bidragTIlgangskontrollConsumer.sjekkTilgangSak(saksnummer)) ingenTilgang("Ingen tilgang til saksnummer $saksnummer")
    }

    fun sjekkTilgangForsendelse(forsendelse: Forsendelse) {
        sjekkTilgangSak(forsendelse.saksnummer)
        sjekkTilgangPerson(forsendelse.gjelderIdent)
    }

    fun sjekkTilgangPerson(personnummer: String) {
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return
        if (!bidragTIlgangskontrollConsumer.sjekkTilgangPerson(personnummer)) ingenTilgang("Ingen tilgang til person $personnummer")
    }

    fun harTilgangTilTema(tema: String): Boolean {
        if (SikkerhetsKontekst.erIApplikasjonKontekst()) return true
        return bidragTIlgangskontrollConsumer.sjekkTilgangTema(tema)
    }
}
