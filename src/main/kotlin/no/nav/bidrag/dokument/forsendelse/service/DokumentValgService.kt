package no.nav.bidrag.dokument.forsendelse.service

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandling
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadFra
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.SoknadType
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.charset.StandardCharsets

@Component
class DokumentValgService(val bestillingConsumer: BidragDokumentBestillingConsumer) {

    val dokumentValgMap: Map<String, DokumentBehandling>

    init {
        dokumentValgMap = fetchDokumentValgMapFromFile()
    }

    fun hentDokumentMalListe(
        behandlingType: BehandlingType,
        soknadType: SoknadType,
        soknadFra: SoknadFra,
        erVedtakFattet: Boolean,
        manuelBeregning: Boolean,
        klage: Boolean
    ): Map<String, DokumentMalDetaljer> {
        val malIder = dokumentValgMap.keys.filter { malId ->
            val detaljListe = dokumentValgMap[malId]
            detaljListe?.detaljer?.any {
                (it.klage == null || it.klage == klage) &&
                        (it.fattetVedtak == null || it.fattetVedtak == erVedtakFattet)
                        && it.behandlingType.contains(behandlingType)
                        && it.soknadType == soknadType
                        && it.soknadFra.contains(soknadFra)
                        && (it.manuelBeregning == null || it.manuelBeregning == manuelBeregning)
            } == true
        }
        return malIder.associateWith { mapToMalDetaljer(it) }
    }

    fun mapToMalDetaljer(malId: String): DokumentMalDetaljer {
        val dokumentDetaljer = bestillingConsumer.dokumentmalDetaljer()
        val malInfo = dokumentDetaljer[malId]
        val tittel = malInfo?.beskrivelse ?: dokumentValgMap[malId]?.tittel ?: "Ukjent"
        val malType = malInfo?.type ?: DokumentMalType.UTGÅENDE
        return DokumentMalDetaljer(tittel, malType)
    }

    private fun fetchDokumentValgMapFromFile(): Map<String, DokumentBehandling> {
        return try {
            val objectMapper = ObjectMapper(YAMLFactory())
            objectMapper.findAndRegisterModules()
            val inputstream = ClassPathResource("files/dokument_valg.json").inputStream
            val text = String(inputstream.readAllBytes(), StandardCharsets.UTF_8)
            val listType: JavaType = objectMapper.typeFactory.constructParametricType(
                MutableList::class.java,
                DokumentBehandling::class.java
            )
            val stringType = objectMapper.typeFactory.constructType(String::class.java)
            val dokbehtyp = objectMapper.typeFactory.constructType(DokumentBehandling::class.java)
            objectMapper.readValue(
                text, objectMapper.typeFactory.constructMapType(
                    MutableMap::class.java, stringType, dokbehtyp
                )
            )
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke laste fil", e)
        }
    }
}