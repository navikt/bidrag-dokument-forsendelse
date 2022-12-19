package no.nav.bidrag.template.service

import no.nav.bidrag.template.consumer.BidragPersonConsumer
import no.nav.bidrag.template.model.HentPersonResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ExampleService(var bidragPersonConsumer: BidragPersonConsumer) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ExampleService::class.java)
    }

    fun hentDialogerForPerson(personId: String): HentPersonResponse? {
        LOGGER.info("Henter samtalereferat for person")
        return bidragPersonConsumer.hentPerson(personId)
    }
}