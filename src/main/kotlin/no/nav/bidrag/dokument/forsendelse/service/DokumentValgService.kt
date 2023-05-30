package no.nav.bidrag.dokument.forsendelse.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import no.nav.bidrag.behandling.felles.enums.VedtakType
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandling
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandlingDetaljer
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.isValid
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.charset.StandardCharsets

@Component
class DokumentValgService(val bestillingConsumer: BidragDokumentBestillingConsumer) {

    val dokumentValgMap: Map<BehandlingType, List<DokumentBehandlingDetaljer>>

    val standardBrevkoder = listOf("BI01S02", "BI01S10", "BI01S67")
    val ekstraBrevkoderVedtakFattet = listOf("BI01S02", "BI01S10")
    val ekstraBrevkoderVedtakIkkeFattet = listOf("BI01S02")
    val notaterBrevkoder = listOf("BI01P11", "BI01P18", "BI01X01", "BI01X02")

    init {
        dokumentValgMap = fetchDokumentValgMapFromFile()
    }

    fun hentNotatListe(): Map<String, DokumentMalDetaljer> {
        return notaterBrevkoder.associateWith { mapToMalDetaljer(it) }
    }

    fun hentDokumentMalListe(
        vedtakType: VedtakType? = null,
        behandlingType: BehandlingType? = null,
        soknadFra: SoknadFra? = null,
        erFattetBeregnet: Boolean? = null,
        enhet: String? = null,
    ): Map<String, DokumentMalDetaljer> {
        val behandlingTypeConverted = if (behandlingType == "GEBYR_MOTTAKER") "GEBYR_SKYLDNER" else behandlingType
        if (behandlingType == null) return standardBrevkoder.associateWith { mapToMalDetaljer(it) }
        val dokumentValg = dokumentValgMap[behandlingTypeConverted]?.find {
            it.soknadFra.contains(soknadFra) &&
                    it.vedtakType.contains(vedtakType) &&
                    it.behandlingStatus.isValid(erFattetBeregnet) &&
                    it.forvaltning.isValid(enhet)
        }
        val brevkoder =
            dokumentValg?.brevkoder?.let { if (erFattetBeregnet != null) it + ekstraBrevkoderVedtakFattet else it + ekstraBrevkoderVedtakIkkeFattet }
                ?: ekstraBrevkoderVedtakIkkeFattet
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
            objectMapper.findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            val inputstream = ClassPathResource("files/dokument_valg.json").inputStream
            val text = String(inputstream.readAllBytes(), StandardCharsets.UTF_8)
            val listType: JavaType = objectMapper.typeFactory.constructParametricType(
                MutableList::class.java,
                DokumentBehandlingDetaljer::class.java
            )
            val stringType = objectMapper.typeFactory.constructType(String::class.java)
            val dokbehtyp = objectMapper.typeFactory.constructType(DokumentBehandling::class.java)
            objectMapper.readValue(
                text, objectMapper.typeFactory.constructMapType(
                    MutableMap::class.java, stringType, listType
                )
            )
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke laste fil", e)
        }
    }
}