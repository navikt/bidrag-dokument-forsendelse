package no.nav.bidrag.dokument.forsendelse.konsumenter

import no.nav.bidrag.commons.security.service.SecurityTokenService
import no.nav.bidrag.dokument.dto.DistribuerJournalpostRequest
import no.nav.bidrag.dokument.dto.DistribuerJournalpostResponse
import no.nav.bidrag.dokument.dto.DistribuerTilAdresse
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.konfigurasjon.CacheConfig.Companion.SAKSBEHANDLERINFO_CACHE
import no.nav.bidrag.dokument.forsendelse.konsumenter.dto.SaksbehandlerInfoResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class BidragDokumentKonsumer(
    @Value("\${BIDRAG_DOKUMENT_URL}") bidragDokument: String,
    baseRestTemplate: RestTemplate,
    securityTokenService: SecurityTokenService
): DefaultConsumer("bidrag-dokument", bidragDokument, baseRestTemplate, securityTokenService) {

    fun opprettJournalpost(opprettJournalpostRequest: OpprettJournalpostRequest): OpprettJournalpostResponse? {
        return restTemplate.exchange(
            "/journalpost/JOARK",
            HttpMethod.POST,
            HttpEntity(opprettJournalpostRequest),
            OpprettJournalpostResponse::class.java
        ).body
    }

    fun hentDokument(journalpostId: String, dokumentId: String?): ByteArray? {
        return restTemplate.exchange(
            "/dokument/$journalpostId/$dokumentId?optimizeForPrint=false",
            HttpMethod.GET,
            null,
            ByteArray::class.java
        ).body
    }
    fun distribuer(journalpostId: String, adresse: DistribuerTilAdresse? = null): DistribuerJournalpostResponse? {
        return restTemplate.exchange(
            "/journal/distribuer/$journalpostId",
            HttpMethod.POST,
            adresse?.let { HttpEntity(DistribuerJournalpostRequest(adresse = it))},
            DistribuerJournalpostResponse::class.java
        ).body
    }
}