package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.consumer.KodeverkConsumer
import org.springframework.stereotype.Service


@Service
class KodeverkService(private val kodeverkConsumer: KodeverkConsumer) {
    private fun hentPoststedFraPostnummer(postnummer: String?): String? = kodeverkConsumer.hentPostnummre().hentFraKode(postnummer)?.hentNorskNavn()

    companion object {
        fun hentNorskPoststed(postnummer: String?, landkode: String?): String? {
            if (landkode != "NO" && landkode != "NOR") return null
            return AppContext.getBean(KodeverkService::class.java).hentPoststedFraPostnummer(postnummer)
        }
    }
}

