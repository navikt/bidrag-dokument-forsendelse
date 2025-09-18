package no.nav.bidrag.dokument.forsendelse.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import no.nav.bidrag.dokument.forsendelse.config.UnleashFeatures
import no.nav.bidrag.dokument.forsendelse.consumer.BidragBehandlingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.model.HentDokumentValgResponse
import no.nav.bidrag.dokument.forsendelse.model.ResultatKode
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.BehandlingType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandlingDetaljer
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentBehandlingTittelDetaljer
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.erVedtakTilbakekrevingLik
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.isValid
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.isVedtaktypeValid
import no.nav.bidrag.dokument.forsendelse.utvidelser.gjelderKlage
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.vedtak.response.erOrkestrertVedtak
import no.nav.bidrag.transport.behandling.vedtak.response.finnResultatFraAnnenVedtak
import no.nav.bidrag.transport.behandling.vedtak.response.omgjøringsvedtakErEnesteVedtak
import no.nav.bidrag.transport.dokument.forsendelse.HentDokumentValgRequest
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.charset.StandardCharsets

val brevkodeAldersjustering = "BI01B05"
val brevkodeForsideVedtak = "VOFORSIDE"

@Component
class DokumentValgService(
    val bestillingConsumer: BidragDokumentBestillingConsumer,
    val bidragVedtakConsumer: BidragVedtakConsumer,
    val behandlingConsumer: BidragBehandlingConsumer,
    val tittelService: ForsendelseTittelService,
) {
    @Suppress("ktlint:standard:property-naming")
    val FRITEKSTBREV = "BI01S02"
    lateinit var dokumentValgMap: Map<BehandlingType, List<DokumentBehandlingDetaljer>>
    lateinit var dokumentValgTittelMap: Map<BehandlingType, List<DokumentBehandlingTittelDetaljer>>

    val standardBrevkoder = listOf("BI01S02", "BI01S10") // BI01S67 - Adresseforespørsel
    val ekstraBrevkoderVedtakFattet = listOf("BI01S02", "BI01S10")
    val ekstraBrevkoderVedtakIkkeFattet = listOf("BI01S02", "BI01S10")
    val notaterBrevkoder = listOf("BI01P11", "BI01P18", "BI01X01", "BI01X02")
    val notaterKlage = listOf("BI01P17")

    init {
        dokumentValgMap = fetchDokumentValgMapFromFile()
        dokumentValgTittelMap = fetchDokumentValgTitlerMapFromFile()
    }

    fun hentNotatListe(request: HentDokumentValgRequest? = null): Map<String, DokumentMalDetaljer> =
        if (erKlage(request)) {
            (notaterKlage + notaterBrevkoder).associateWith { mapToMalDetaljer(it, request, true) }
        } else {
            notaterBrevkoder.associateWith { mapToMalDetaljer(it, request, true) }
        }

    fun erKlage(request: HentDokumentValgRequest? = null): Boolean =
        if (request == null) {
            false
        } else if (request.erKlage()) {
            true
        } else if (!request.vedtakId.isNullOrEmpty() && UnleashFeatures.DOKUMENTVALG_FRA_VEDTAK_BEHANDLING.isEnabled) {
            bidragVedtakConsumer.hentVedtak(vedtakId = request.vedtakId!!)?.let { it.type == Vedtakstype.KLAGE }
                ?: false
        } else if (!request.behandlingId.isNullOrEmpty()) {
            behandlingConsumer
                .hentBehandling(behandlingId = request.behandlingId!!)
                ?.let { it.vedtakstype == Vedtakstype.KLAGE } ?: false
        } else {
            false
        }

    fun hentDokumentMalListe(request: HentDokumentValgRequest? = null): Map<String, DokumentMalDetaljer> =
        hentDokumentMalListeV2(request).dokumentMalDetaljer

    fun hentDokumentMalListeV2(request: HentDokumentValgRequest? = null): HentDokumentValgResponse {
        if (request == null) return HentDokumentValgResponse(standardBrevkoder.associateWith { mapToMalDetaljer(it) })
        val requestUtfylt = hentUtfyltDokumentValgDetaljer(request)
        val maler =
            hentDokumentMalListeForRequest(requestUtfylt)
                ?: standardBrevkoder.associateWith { mapToMalDetaljer(it, request) }

        val automatiskOpprettDokumenter = bestemDokumentMallisteForVedtak(request)
        return HentDokumentValgResponse(
            maler
                .toList()
                .sortedBy { (a, b) ->
                    val automatiskOpprettDokumenterIds = automatiskOpprettDokumenter.map { it.malId }
                    if (automatiskOpprettDokumenterIds.contains(a)) {
                        automatiskOpprettDokumenterIds.indexOf(a)
                    } else if (a == FRITEKSTBREV) {
                        Int.MAX_VALUE
                    } else {
                        automatiskOpprettDokumenterIds.size
                    }
                }.toMap(),
            bestemDokumentMallisteForVedtak(request),
        )
    }

    private fun bestemDokumentMallisteForVedtak(request: HentDokumentValgRequest? = null): List<DokumentMalDetaljer> {
        return if (request?.vedtakId != null) {
            val it = bidragVedtakConsumer.hentVedtak(vedtakId = request.vedtakId!!) ?: return emptyList()
            if (!it.erOrkestrertVedtak) {
                return emptyList()
            }
            val virkningstidspunktGrunnlag =
                it.grunnlagListe
                    .filtrerOgKonverterBasertPåEgenReferanse<VirkningstidspunktGrunnlag>(
                        Grunnlagstype.VIRKNINGSTIDSPUNKT,
                    )

            val erDirekteAvslag =
                virkningstidspunktGrunnlag.isNotEmpty() && virkningstidspunktGrunnlag.all { it.innhold.avslag != null }
            val inneholderAldersjustering =
                it.erOrkestrertVedtak &&
                    it.stønadsendringListe.any { s ->
                        s.periodeListe.any { p ->
                            val resultatFraAnnenVedtak = it.grunnlagListe.finnResultatFraAnnenVedtak(p.grunnlagReferanseListe)
                            val vedtakstype =
                                resultatFraAnnenVedtak?.vedtakstype ?: run {
                                    resultatFraAnnenVedtak?.vedtaksid?.let {
                                        bidragVedtakConsumer.hentVedtak(vedtakId = it.toString())?.type
                                    }
                                }
                            vedtakstype == Vedtakstype.ALDERSJUSTERING && resultatFraAnnenVedtak != null
                        }
                    }

            val dokumentmalListe = mutableListOf<String>()
            if (!it.omgjøringsvedtakErEnesteVedtak) {
                dokumentmalListe.add(brevkodeForsideVedtak)
            }
            if (!erDirekteAvslag) {
                dokumentmalListe.add("BI01B50")
            }
            if (inneholderAldersjustering) {
                dokumentmalListe.add(brevkodeAldersjustering)
            }
            dokumentmalListe.map { mapToMalDetaljer(it, request, false) }
        } else {
            emptyList()
        }
    }

    private fun hentUtfyltDokumentValgDetaljer(request: HentDokumentValgRequest? = null): HentDokumentValgRequest? =
        if (request == null) {
            null
        } else if (request.vedtakId != null && UnleashFeatures.DOKUMENTVALG_FRA_VEDTAK_BEHANDLING.isEnabled) {
            bidragVedtakConsumer
                .hentVedtak(vedtakId = request.vedtakId!!)
                ?.let {
                    val behandlingType =
                        if (it.stønadsendringListe.isNotEmpty()) it.stønadsendringListe[0].type.name else it.engangsbeløpListe[0].type.name
                    val virkningstidspunktGrunnlag =
                        it.grunnlagListe
                            .filtrerOgKonverterBasertPåEgenReferanse<VirkningstidspunktGrunnlag>(
                                Grunnlagstype.VIRKNINGSTIDSPUNKT,
                            )
                    val erDirekteAvslag =
                        virkningstidspunktGrunnlag.isNotEmpty() && virkningstidspunktGrunnlag.all { it.innhold.avslag != null }
                    val erFattetBeregnet =
                        it.type != Vedtakstype.INNKREVING && it.grunnlagListe.any { gr -> gr.type.name.startsWith("DELBEREGNING") } ||
                            it.kildeapplikasjon.startsWith("bidrag-behandling")
                    val erVedtakIkkeTilbakekreving = it.engangsbeløpListe.any { gr -> gr.resultatkode == ResultatKode.IKKE_TILBAKEKREVING }
                    val inneholderAldersjustering =
                        it.erOrkestrertVedtak &&
                            it.stønadsendringListe.any { s ->
                                s.periodeListe.any { p ->
                                    val resultatFraAnnenVedtak = it.grunnlagListe.finnResultatFraAnnenVedtak(p.grunnlagReferanseListe)
                                    val vedtakstype =
                                        resultatFraAnnenVedtak?.vedtakstype ?: run {
                                            resultatFraAnnenVedtak?.vedtaksid?.let {
                                                bidragVedtakConsumer.hentVedtak(vedtakId = it.toString())?.type
                                            }
                                        }
                                    vedtakstype == Vedtakstype.ALDERSJUSTERING && resultatFraAnnenVedtak != null
                                }
                            }
                    request.copy(
                        behandlingType = behandlingType,
                        vedtakType = it.type,
                        erFattetBeregnet = erFattetBeregnet,
                        erOrkestrertVedtak = it.erOrkestrertVedtak && !it.omgjøringsvedtakErEnesteVedtak,
                        inneholderAldersjustering = inneholderAldersjustering,
                        erVedtakIkkeTilbakekreving = erVedtakIkkeTilbakekreving,
                        enhet = request.enhet ?: it.enhetsnummer?.verdi,
                    )
                }
        } else if (request.behandlingId != null &&
            request.erFattetBeregnet == null &&
            UnleashFeatures.DOKUMENTVALG_FRA_VEDTAK_BEHANDLING.isEnabled
        ) {
            behandlingConsumer
                .hentBehandling(
                    request.behandlingId!!,
                )?.let {
                    request.copy(
                        behandlingType = it.stønadstype?.name ?: it.engangsbeløptype?.name,
                        vedtakType = it.vedtakstype,
                        soknadFra = request.soknadFra ?: it.søktAv,
                        erFattetBeregnet = null,
                        erVedtakIkkeTilbakekreving = false,
                        enhet = request.enhet ?: it.behandlerenhet,
                    )
                }
        } else {
            request
        }

    private fun hentDokumentMalListeForRequest(request: HentDokumentValgRequest?): Map<String, DokumentMalDetaljer>? {
        if (request == null) return null
        val (soknadType, vedtakType, _, soknadFra, erFattetBeregnet, erVedtakIkkeTilbakekreving, _, _, enhet) = request
        val behandlingType = request.behandlingtypeKonvertert
        val behandlingTypeConverted = if (behandlingType == "GEBYR_MOTTAKER") "GEBYR_SKYLDNER" else behandlingType
        val dokumentValg =
            dokumentValgMap[behandlingTypeConverted]
                ?.find {
                    it.soknadFra.contains(soknadFra) &&
                        it.isVedtaktypeValid(vedtakType, soknadType) &&
                        it.behandlingStatus.isValid(erFattetBeregnet) &&
                        it.forvaltning.isValid(enhet) &&
                        it.erVedtakIkkeTilbakekreving == erVedtakIkkeTilbakekreving
                }?.let {
                    val ekstraKoder = mutableListOf<String>()
                    if (request.inneholderAldersjustering == true) {
                        ekstraKoder.add(brevkodeAldersjustering)
                    }
                    if (request.erOrkestrertVedtak == true) {
                        ekstraKoder.add(brevkodeForsideVedtak)
                    }
                    it.copy(
                        brevkoder = it.brevkoder + ekstraKoder,
                    )
                }

        val brevkoder =
            dokumentValg?.brevkoder?.let {
                if (erFattetBeregnet != null) {
                    it + ekstraBrevkoderVedtakFattet
                } else {
                    it + ekstraBrevkoderVedtakIkkeFattet
                }
            }
                ?: if (erFattetBeregnet != null) ekstraBrevkoderVedtakFattet else ekstraBrevkoderVedtakIkkeFattet
        return brevkoder
            .associateWith { mapToMalDetaljer(it, request) }
            .filter { it.value.type != DokumentMalType.NOTAT }
    }

    fun mapToMalDetaljer(
        malId: String,
        request: HentDokumentValgRequest? = null,
        leggTilPrefiksPåTittel: Boolean = false,
    ): DokumentMalDetaljer {
        val dokumentDetaljer = bestillingConsumer.dokumentmalDetaljer()
        val malInfo = dokumentDetaljer[malId]
        val originalTittel = malInfo?.tittel ?: "Ukjent"
        val malType = malInfo?.type ?: DokumentMalType.UTGÅENDE
        val tittel =
            if (leggTilPrefiksPåTittel) {
                tittelService.hentTittelMedPrefiks(
                    originalTittel,
                    request?.tilBehandlingInfo(),
                )
            } else if (malId == brevkodeForsideVedtak) {
                request?.tilBehandlingInfo()?.gjelderKlage()?.ifTrue {
                    originalTittel.replace("omgjøringsvedtak", "klagevedtak")
                } ?: originalTittel
            } else {
                originalTittel
            }
        return DokumentMalDetaljer(
            malId,
            tittel,
            beskrivelse = malInfo?.beskrivelse ?: tittel,
            type = malType,
            alternativeTitler = hentAlternativeTitlerForMal(malId, request),
        )
    }

    fun hentAlternativeTitlerForMal(
        malId: String,
        request: HentDokumentValgRequest? = null,
    ): List<String> {
        if (request == null) return emptyList()
        return dokumentValgTittelMap[malId]
            ?.sortedByDescending {
                it.isVedtaktypeValid(
                    request.vedtakType,
                    request.soknadType,
                ) ||
                    it.soknadFra.contains(request.soknadFra)
            }?.find {
                (it.soknadFra.isEmpty() || it.soknadFra.contains(request.soknadFra)) &&
                    (it.vedtakType.isEmpty() || it.isVedtaktypeValid(request.vedtakType, request.soknadType)) &&
                    listOf(
                        it.stonadType?.name,
                        it.engangsbelopType?.name,
                        it.behandlingType,
                    ).contains(request.behandlingtypeKonvertert) &&
                    it.behandlingStatus.isValid(request.erFattetBeregnet) &&
                    (it.forvaltning == null || it.forvaltning.isValid(request.enhet)) &&
                    it.erVedtakTilbakekrevingLik(request.erVedtakIkkeTilbakekreving)
            }?.titler ?: emptyList()
    }

    private fun fetchDokumentValgMapFromFile(): Map<BehandlingType, List<DokumentBehandlingDetaljer>> =
        try {
            val objectMapper = ObjectMapper(YAMLFactory())
            objectMapper.findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val inputstream = ClassPathResource("files/dokument_valg.json").inputStream
            val text = String(inputstream.readAllBytes(), StandardCharsets.UTF_8)
            val listType: JavaType =
                objectMapper.typeFactory.constructParametricType(
                    MutableList::class.java,
                    DokumentBehandlingDetaljer::class.java,
                )
            val stringType = objectMapper.typeFactory.constructType(String::class.java)
            objectMapper.readValue(
                text,
                objectMapper.typeFactory.constructMapType(
                    MutableMap::class.java,
                    stringType,
                    listType,
                ),
            )
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke laste fil", e)
        }

    private fun fetchDokumentValgTitlerMapFromFile(): Map<BehandlingType, List<DokumentBehandlingTittelDetaljer>> =
        try {
            val objectMapper = ObjectMapper(YAMLFactory())
            objectMapper.findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val inputstream = ClassPathResource("files/dokument_valg_tittel.json").inputStream
            val text = String(inputstream.readAllBytes(), StandardCharsets.UTF_8)
            val listType: JavaType =
                objectMapper.typeFactory.constructParametricType(
                    MutableList::class.java,
                    DokumentBehandlingTittelDetaljer::class.java,
                )
            val stringType = objectMapper.typeFactory.constructType(String::class.java)
            objectMapper.readValue(
                text,
                objectMapper.typeFactory.constructMapType(
                    MutableMap::class.java,
                    stringType,
                    listType,
                ),
            )
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke laste fil", e)
        }
}

fun HentDokumentValgRequest.tilBehandlingInfo(): BehandlingInfo =
    BehandlingInfo(
        vedtakId = this.vedtakId,
        behandlingId = this.behandlingId,
        vedtakType = this.vedtakType,
        engangsBelopType = this.engangsBelopType,
        stonadType = this.stonadType,
        soknadType = this.soknadType,
        erFattetBeregnet = this.erFattetBeregnet,
        erVedtakIkkeTilbakekreving = this.erVedtakIkkeTilbakekreving,
        soknadFra = this.soknadFra,
        behandlingType = this.behandlingType,
    )
