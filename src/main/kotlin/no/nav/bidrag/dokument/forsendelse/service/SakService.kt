package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.consumer.BidragSakConsumer
import no.nav.bidrag.transport.sak.BidragssakDto
import org.springframework.stereotype.Service

@Service
class SakService(
    private val bidragSakConsumer: BidragSakConsumer,
) {
    fun hentSak(saksnummer: String): BidragssakDto? =
        try {
            bidragSakConsumer.hentSak(saksnummer)
        } catch (e: Exception) {
            null
        }
}
