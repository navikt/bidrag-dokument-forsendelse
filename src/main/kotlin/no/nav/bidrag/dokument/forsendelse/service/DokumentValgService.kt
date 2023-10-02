package no.nav.bidrag.dokument.forsendelse.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.api.dto.tilBehandlingInfo
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.model.ResultatKode
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandlingDetaljer
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandlingTittelDetaljer
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.erVedtakTilbakekrevingLik
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.isValid
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.isVedtaktypeValid
import no.nav.bidrag.domain.enums.GrunnlagType
import no.nav.bidrag.domain.enums.VedtakType
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.charset.StandardCharsets

@Component
class DokumentValgService(
    val bestillingConsumer: BidragDokumentBestillingConsumer,
    val bidragVedtakConsumer: BidragVedtakConsumer,
    val behandlingConsumer: BidragBehandlingConsumer,
    val tittelService: ForsendelseTittelService
) {

    val FRITEKSTBREV = "BI01S02"
    val dokumentValgMap: Map<BehandlingType, List<DokumentBehandlingDetaljer>>
    val dokumentValgTittelMap: Map<BehandlingType, List<DokumentBehandlingTittelDetaljer>>

    val standardBrevkoder = listOf("BI01S02", "BI01S10") // BI01S67 - Adresseforespørsel
    val ekstraBrevkoderVedtakFattet = listOf("BI01S02", "BI01S10")
    val ekstraBrevkoderVedtakIkkeFattet = listOf("BI01S02", "BI01S10")
    val notaterBrevkoder = listOf("BI01P11", "BI01P18", "BI01X01", "BI01X02")
    val notaterKlage = listOf("BI01P17")

    init {
        dokumentValgMap = fetchDokumentValgMapFromFile()
        dokumentValgTittelMap = fetchDokumentValgTitlerMapFromFile()
    }

    fun hentNotatListe(request: HentDokumentValgRequest? = null): Map<String, DokumentMalDetaljer> {
        return if (erKlage(request)) (notaterKlage + notaterBrevkoder).associateWith { mapToMalDetaljer(it, request, true) }
        else notaterBrevkoder.associateWith { mapToMalDetaljer(it, request, true) }
    }

    fun hentVedleggListe(request: HentDokumentValgRequest? = null): Map<String, DokumentMalDetaljer> {
        return if (erKlage(request)) (notaterKlage + notaterBrevkoder).associateWith { mapToMalDetaljer(it, request, true) }
        else notaterBrevkoder.associateWith { mapToMalDetaljer(it, request, true) }
    }

    fun erKlage(request: HentDokumentValgRequest? = null): Boolean {
        return if (request == null) false
        else if (request.erKlage()) true
        else if (!request.vedtakId.isNullOrEmpty())
            bidragVedtakConsumer.hentVedtak(vedtakId = request.vedtakId)?.let { it.type == VedtakType.KLAGE }
                ?: false
        else if (!request.behandlingId.isNullOrEmpty()) behandlingConsumer.hentBehandling(behandlingId = request.behandlingId)
            ?.let { it.soknadType == VedtakType.KLAGE } ?: false
        else false
    }

    fun hentDokumentMalListe(
        request: HentDokumentValgRequest? = null
    ): Map<String, DokumentMalDetaljer> {
        if (request == null) return standardBrevkoder.associateWith { mapToMalDetaljer(it) }
        val requestUtfylt = hentUtfyltDokumentValgDetaljer(request)
        val maler = hentDokumentMalListeForRequest(requestUtfylt)
            ?: standardBrevkoder.associateWith { mapToMalDetaljer(it, request) }

        return maler.toList().sortedBy { (a, b) -> if (a == FRITEKSTBREV) -1 else 1 }.toMap()
    }

    private fun hentUtfyltDokumentValgDetaljer(request: HentDokumentValgRequest? = null): HentDokumentValgRequest? {
        return if (request == null) null
        else if (request.vedtakId != null) bidragVedtakConsumer.hentVedtak(vedtakId = request.vedtakId)?.let {
            val behandlingType =
                if (it.stonadsendringListe.isNotEmpty()) it.stonadsendringListe[0].type.name else it.engangsbelopListe[0].type.name
            val erFattetBeregnet = it.grunnlagListe.any { gr -> gr.type == GrunnlagType.SLUTTBEREGNING_BBM }
            val erVedtakIkkeTilbakekreving = it.engangsbelopListe.any { gr -> gr.resultatkode == ResultatKode.IKKE_TILBAKEKREVING }
            request.copy(
                behandlingType = behandlingType,
                vedtakType = it.type,
                erFattetBeregnet = erFattetBeregnet,
                erVedtakIkkeTilbakekreving = erVedtakIkkeTilbakekreving,
                enhet = request.enhet ?: it.enhetId,
            )
        }
        else if (request.behandlingId != null && request.erFattetBeregnet == null) behandlingConsumer.hentBehandling(request.behandlingId)?.let {
            request.copy(
                behandlingType = it.behandlingType,
                vedtakType = it.soknadType,
                soknadFra = request.soknadFra ?: it.soknadFraType,
                erFattetBeregnet = null,
                erVedtakIkkeTilbakekreving = false,
                enhet = request.enhet ?: it.behandlerEnhet,
            )
        }
        else request
    }

    private fun hentDokumentMalListeForRequest(
        request: HentDokumentValgRequest?
    ): Map<String, DokumentMalDetaljer>? {
        if (request == null) return null
        val (soknadType, vedtakType, behandlingType, soknadFra, erFattetBeregnet, erVedtakIkkeTilbakekreving, _, _, enhet) = request
        val behandlingTypeConverted = if (behandlingType == "GEBYR_MOTTAKER") "GEBYR_SKYLDNER" else behandlingType
        val dokumentValg = dokumentValgMap[behandlingTypeConverted]?.find {
            it.soknadFra.contains(soknadFra) &&
                    it.isVedtaktypeValid(vedtakType, soknadType) &&
                    it.behandlingStatus.isValid(erFattetBeregnet) &&
                    it.forvaltning.isValid(enhet) &&
                    it.erVedtakIkkeTilbakekreving == erVedtakIkkeTilbakekreving
        }
        val brevkoder =
            dokumentValg?.brevkoder?.let { if (erFattetBeregnet != null) it + ekstraBrevkoderVedtakFattet else it + ekstraBrevkoderVedtakIkkeFattet }
                ?: if (erFattetBeregnet != null) ekstraBrevkoderVedtakFattet else ekstraBrevkoderVedtakIkkeFattet
        return brevkoder.associateWith { mapToMalDetaljer(it, request) }
            .filter { it.value.type != DokumentMalType.NOTAT }
    }

    fun mapToMalDetaljer(malId: String, request: HentDokumentValgRequest? = null, leggTilPrefiksPåTittel: Boolean = false): DokumentMalDetaljer {
        val dokumentDetaljer = bestillingConsumer.dokumentmalDetaljer()
        val malInfo = dokumentDetaljer[malId]
        val originalTittel = malInfo?.beskrivelse ?: "Ukjent"
        val malType = malInfo?.type ?: DokumentMalType.UTGÅENDE
        val tittel = if (leggTilPrefiksPåTittel) tittelService.hentTittelMedPrefiks(originalTittel, request?.tilBehandlingInfo()) else originalTittel
        return DokumentMalDetaljer(tittel, type = malType, alternativeTitler = hentAlternativeTitlerForMal(malId, request))
    }

    fun hentAlternativeTitlerForMal(malId: String, request: HentDokumentValgRequest? = null): List<String> {
        if (request == null) return emptyList()
        return dokumentValgTittelMap[malId]?.find {
            (it.soknadFra.isEmpty() || it.soknadFra.contains(request.soknadFra)) &&
                    it.isVedtaktypeValid(request.vedtakType, request.soknadType) &&
                    listOf(it.stonadType?.name, it.engangsbelopType?.name).contains(request.behandlingType) &&
                    it.behandlingStatus.isValid(request.erFattetBeregnet) &&
                    (it.forvaltning == null || it.forvaltning.isValid(request.enhet)) &&
                    it.erVedtakTilbakekrevingLik(request.erVedtakIkkeTilbakekreving)
        }?.titler ?: emptyList()
    }

    private fun fetchDokumentValgMapFromFile(): Map<BehandlingType, List<DokumentBehandlingDetaljer>> {
        return try {
            val objectMapper = ObjectMapper(YAMLFactory())
            objectMapper.findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val inputstream = ClassPathResource("files/dokument_valg.json").inputStream
            val text = String(inputstream.readAllBytes(), StandardCharsets.UTF_8)
            val listType: JavaType = objectMapper.typeFactory.constructParametricType(
                MutableList::class.java,
                DokumentBehandlingDetaljer::class.java
            )
            val stringType = objectMapper.typeFactory.constructType(String::class.java)
            objectMapper.readValue(
                text,
                objectMapper.typeFactory.constructMapType(
                    MutableMap::class.java,
                    stringType,
                    listType
                )
            )
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke laste fil", e)
        }
    }

    private fun fetchDokumentValgTitlerMapFromFile(): Map<BehandlingType, List<DokumentBehandlingTittelDetaljer>> {
        return try {
            val objectMapper = ObjectMapper(YAMLFactory())
            objectMapper.findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val inputstream = ClassPathResource("files/dokument_valg_tittel.json").inputStream
            val text = String(inputstream.readAllBytes(), StandardCharsets.UTF_8)
            val listType: JavaType = objectMapper.typeFactory.constructParametricType(
                MutableList::class.java,
                DokumentBehandlingTittelDetaljer::class.java
            )
            val stringType = objectMapper.typeFactory.constructType(String::class.java)
            objectMapper.readValue(
                text,
                objectMapper.typeFactory.constructMapType(
                    MutableMap::class.java,
                    stringType,
                    listType
                )
            )
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke laste fil", e)
        }
    }
}
