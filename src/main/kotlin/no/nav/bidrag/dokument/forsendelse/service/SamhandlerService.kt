package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.dokument.forsendelse.consumer.BidragSamhandlerConsumer
import no.nav.bidrag.dokument.forsendelse.utvidelser.takeIfNotNullOrEmpty
import no.nav.bidrag.transport.samhandler.SamhandlerDto

fun hentSamhandler(samhandlerId: String?): SamhandlerDto? =
    try {
        samhandlerId.takeIfNotNullOrEmpty {
            AppContext.getBean(BidragSamhandlerConsumer::class.java).hentSamhandler(it)
        }
    } catch (e: Exception) {
        secureLogger.debug(e) { "Feil ved henting av samhandler $samhandlerId" }
        null
    }
