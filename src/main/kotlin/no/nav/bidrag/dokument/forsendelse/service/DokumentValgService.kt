package no.nav.bidrag.dokument.forsendelse.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import no.nav.bidrag.dokument.forsendelse.api.dto.HentDokumentValgRequest
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.model.ResultatKode
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandling
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandlingDetaljer
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadType
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
    val behandlingConsumer: BidragBehandlingConsumer
) {

    val dokumentValgMap: Map<BehandlingType, List<DokumentBehandlingDetaljer>>

    val standardBrevkoder = listOf("BI01S02", "BI01S10") // BI01S67 - Adresseforespørsel
    val ekstraBrevkoderVedtakFattet = listOf("BI01S02", "BI01S10")
    val ekstraBrevkoderVedtakIkkeFattet = listOf("BI01S02", "BI01S10")
    val notaterBrevkoder = listOf("BI01P11", "BI01P18", "BI01X01", "BI01X02")
    val notaterKlage = listOf("BI01P17")

    init {
        dokumentValgMap = fetchDokumentValgMapFromFile()
    }

    fun hentNotatListe(request: HentDokumentValgRequest? = null): Map<String, DokumentMalDetaljer> {
        return if (erKlage(request)) (notaterKlage + notaterBrevkoder).associateWith { mapToMalDetaljer(it) }
        else notaterBrevkoder.associateWith { mapToMalDetaljer(it) }
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
        if (request.vedtakId != null) {
            return hentDokumentmalListeForVedtakId(request.vedtakId, request.soknadFra, request.enhet, request.soknadType)
                ?: standardBrevkoder.associateWith { mapToMalDetaljer(it) }
        }
        if (request.behandlingId != null && request.erFattetBeregnet == null) {
            return hentDokumentmalListeForBehandlingId(request.behandlingId, request.soknadFra, request.enhet, request.soknadType)
                ?: standardBrevkoder.associateWith { mapToMalDetaljer(it) }
        }
        val (soknadType, vedtakType, behandlingType, soknadFra, erFattetBeregnet, erVedtakIkkeTilbakekreving, _, _, enhet) = request
        return behandlingType?.let {
            hentDokumentMalListe(
                behandlingType,
                vedtakType,
                soknadFra,
                erFattetBeregnet,
                erVedtakIkkeTilbakekreving,
                enhet,
                soknadType
            )
        }
            ?: standardBrevkoder.associateWith { mapToMalDetaljer(it) }
    }

    private fun hentDokumentmalListeForVedtakId(
        vedtakId: String,
        soknadFra: SoknadFra?,
        enhet: String?,
        soknadType: SoknadType?
    ): Map<String, DokumentMalDetaljer>? {
        return bidragVedtakConsumer.hentVedtak(vedtakId = vedtakId)?.let {
            val behandlingType =
                if (it.stonadsendringListe.isNotEmpty()) it.stonadsendringListe[0].type.name else it.engangsbelopListe[0].type.name
            val erFattetBeregnet = it.grunnlagListe.any { gr -> gr.type == GrunnlagType.SLUTTBEREGNING_BBM }
            val erVedtakIkkeTilbakekreving = it.engangsbelopListe.any { gr -> gr.resultatkode == ResultatKode.IKKE_TILBAKEKREVING }
            return hentDokumentMalListe(
                behandlingType,
                it.type,
                soknadFra,
                erFattetBeregnet,
                erVedtakIkkeTilbakekreving,
                enhet ?: it.enhetId,
                soknadType = soknadType
            )
        }
    }

    private fun hentDokumentmalListeForBehandlingId(
        behandlingId: String,
        soknadFra: SoknadFra?,
        enhet: String?,
        soknadType: SoknadType?
    ): Map<String, DokumentMalDetaljer>? {
        return behandlingConsumer.hentBehandling(behandlingId)?.let {
            return hentDokumentMalListe(
                it.behandlingType,
                it.soknadType,
                soknadFra ?: it.soknadFraType,
                null,
                false,
                enhet ?: it.behandlerEnhet,
                soknadType = soknadType
            )
        }
    }

    private fun hentDokumentMalListe(
        behandlingType: BehandlingType,
        vedtakType: VedtakType? = null,
        soknadFra: SoknadFra? = null,
        erFattetBeregnet: Boolean? = null,
        erVedtakIkkeTilbakekreving: Boolean? = false,
        enhet: String? = null,
        soknadType: SoknadType? = null
    ): Map<String, DokumentMalDetaljer> {
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
        return brevkoder.associateWith { mapToMalDetaljer(it) }.filter { it.value.type != DokumentMalType.NOTAT }
    }

    fun mapToMalDetaljer(malId: String): DokumentMalDetaljer {
        val dokumentDetaljer = bestillingConsumer.dokumentmalDetaljer()
        val malInfo = dokumentDetaljer[malId]
        val tittel = malInfo?.beskrivelse ?: "Ukjent"
        val malType = malInfo?.type ?: DokumentMalType.UTGÅENDE
        return DokumentMalDetaljer(tittel, malType)
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
            val dokbehtyp = objectMapper.typeFactory.constructType(DokumentBehandling::class.java)
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
