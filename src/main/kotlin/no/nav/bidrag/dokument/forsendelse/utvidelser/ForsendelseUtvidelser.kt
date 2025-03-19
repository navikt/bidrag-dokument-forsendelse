package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.consumer.dto.BehandlingDto
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.model.kanIkkeDistribuereForsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Ettersendingsoppgave
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.domene.enums.diverse.Språk
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.dokument.OpprettEttersendingsoppgaveVedleggDto
import no.nav.bidrag.transport.dokument.OpprettEttersendingsppgaveDto

val Forsendelse.dokumentDato get() = dokumenter.hoveddokument?.dokumentDato
val Forsendelse.erNotat get() = forsendelseType == ForsendelseType.NOTAT
val Forsendelse.erUtgående get() = forsendelseType == ForsendelseType.UTGÅENDE
val Forsendelse.forsendelseIdMedPrefix get() = "BIF-$forsendelseId"

fun Forsendelse.validerKanDistribuere() {
    val forsendelseId = this.forsendelseId!!
    if (this.forsendelseType != ForsendelseType.UTGÅENDE) kanIkkeDistribuereForsendelse(forsendelseId, "Forsendelse er ikke utgående")

    if (this.status == ForsendelseStatus.UNDER_PRODUKSJON && !this.dokumenter.erAlleFerdigstilt) {
        kanIkkeDistribuereForsendelse(forsendelseId, "Alle dokumenter er ikke ferdigstilt")
    }
    if (!listOf(ForsendelseStatus.UNDER_PRODUKSJON, ForsendelseStatus.FERDIGSTILT).contains(this.status)) {
        kanIkkeDistribuereForsendelse(
            forsendelseId,
            "Forsendelse har feil status ${this.status}",
        )
    }

    if (this@validerKanDistribuere.ettersendingsoppgave != null) {
        if (this@validerKanDistribuere.ettersendingsoppgave!!.vedleggsliste.isEmpty()) {
            kanIkkeDistribuereForsendelse(
                forsendelseId,
                "Forsendelse har varsel for ettersendelse uten vedleggsliste",
            )
        }
        if (ettersendingsoppgave!!.tittel.isNullOrEmpty()) {
            kanIkkeDistribuereForsendelse(
                forsendelseId,
                "Varsel ettersendelse mangler tittel",
            )
        }
    }
}

fun Forsendelse.kanDistribueres(): Boolean =
    try {
        validerKanDistribuere()
        true
    } catch (_: Exception) {
        false
    }

fun Rolletype.toName(): String? =
    when (this) {
        Rolletype.BIDRAGSMOTTAKER -> "Bidragsmottaker"
        Rolletype.BIDRAGSPLIKTIG -> "Bidragspliktig"
        Rolletype.BARN -> "Barn"
        Rolletype.REELMOTTAKER -> "Reel mottaker"
        else -> null
    }

fun BehandlingInfo.tilBeskrivelseBehandlingType(
    vedtak: VedtakDto? = null,
    behandling: BehandlingDto? = null,
): String? {
    val stønadstypeValue =
        vedtak
            ?.stønadsendringListe
            ?.isNotEmpty()
            ?.ifTrue { vedtak.stønadsendringListe[0].type } ?: behandling?.stønadstype ?: stonadType
    val engangsbeløptypeValue =
        vedtak?.engangsbeløpListe?.isNotEmpty()?.ifTrue {
            vedtak.engangsbeløpListe[0].type
        } ?: behandling?.engangsbeløptype ?: engangsBelopType
    return when (stønadstypeValue) {
        Stønadstype.FORSKUDD -> "Bidragsforskudd"
        Stønadstype.BIDRAG -> "Barnebidrag"
        Stønadstype.BIDRAG18AAR -> "Barnebidrag 18 år"
        Stønadstype.EKTEFELLEBIDRAG -> "Ektefellebidrag"
        Stønadstype.OPPFOSTRINGSBIDRAG -> "Oppfostringbidrag"
        Stønadstype.MOTREGNING -> "Motregning"
        else ->
            when (engangsbeløptypeValue) {
                Engangsbeløptype.SAERTILSKUDD, Engangsbeløptype.SÆRTILSKUDD, Engangsbeløptype.SÆRBIDRAG -> "Særtilskudd"
                Engangsbeløptype.DIREKTE_OPPGJOR, Engangsbeløptype.DIREKTE_OPPGJØR -> "Direkte oppgjør"
                Engangsbeløptype.ETTERGIVELSE -> "Ettergivelse"
                Engangsbeløptype.ETTERGIVELSE_TILBAKEKREVING -> "Ettergivelse tilbakekreving"
                Engangsbeløptype.GEBYR_MOTTAKER -> "Gebyr"
                Engangsbeløptype.GEBYR_SKYLDNER -> "Gebyr"
                Engangsbeløptype.TILBAKEKREVING -> "Tilbakekreving"
                else -> behandlingType?.lowercase()?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
            }
    }
}

fun BehandlingInfo.gjelderKlage(
    vedtak: VedtakDto? = null,
    behandling: BehandlingDto? = null,
) = vedtak?.type == Vedtakstype.KLAGE || behandling?.vedtakstype == Vedtakstype.KLAGE || vedtakType == Vedtakstype.KLAGE

fun BehandlingInfo.tilBeskrivelse(
    rolle: Rolletype?,
    vedtak: VedtakDto? = null,
    behandling: BehandlingDto? = null,
): String {
    val behandlingType = this.tilBeskrivelseBehandlingType(vedtak, behandling)
    val gjelderKlage = this.gjelderKlage(vedtak, behandling)

    val stringBuilder = mutableListOf<String>()
    if (vedtakId.isNotNullOrEmpty() || erFattetBeregnet != null) {
        if (gjelderKlage) stringBuilder.add("Klagevedtak") else stringBuilder.add("Vedtak")
        if (behandlingType != null) {
            stringBuilder.add("om ${behandlingType.lowercase()}")
        }
    } else {
        stringBuilder.add("Orientering/Varsel")
        if (behandlingType != null) {
            if (gjelderKlage) {
                stringBuilder.add("om klage på vedtak om ${behandlingType.lowercase()}")
            } else {
                stringBuilder.add("om ${behandlingType.lowercase()}")
            }
        }
    }

    if (rolle != null) {
        stringBuilder.add("til ${rolle.toName()?.lowercase()}")
    }
    return stringBuilder.joinToString(" ")
}

val vedleggskodeAnnet = "N6"
val vedleggLenkeMap =
    mapOf(
        "N6_FYLLUT_SØKNAD_18_SVAR" to "https://www.nav.no/fyllut/nav540010",
        "N6_FYLLUT_SØKNAD_18" to "https://www.nav.no/fyllut/nav540006",
        "N6_FYLLUT_BIDRAG18ÅR" to "https://www.nav.no/fyllut/nav550063",
        "N6_FYLLUT_BIDRAG" to "https://www.nav.no/fyllut/nav540005",
        "N6_FYLLUT_BIDRAG_SVAR" to "https://www.nav.no/fyllut/nav540004",
        "N6_FYLLUT_FORSKUDD" to "https://www.nav.no/fyllut/nav540009",
        "N6_FYLLUT_SÆRBIDRAG" to "https://www.nav.no/fyllut/nav540013",
        "W3" to
            "https://www.nav.no/_/attachment/inline/7ee5c0c6-7b2c-466d-bb88-2ff51475513b:c1a1c3c4f1ff4518b61142e2394ac3733a271049/Skolens%20bekreftelse%20p%C3%A5%20skolegang.docx",
        "N6_BL" to
            "https://www.nav.no/_/attachment/inline/6066e804-b944-4206-b454-82f5fb8a6940:c9f0c0064dd56ae893beb749d1342afa3e3e7338/Bekreftelse%20p%C3%A5%20nedsatt%20arbeidsevne%20grunnet%20helsemessige%20forhold.docx",
    )

fun Ettersendingsoppgave.tilDto() =
    OpprettEttersendingsppgaveDto(
        tittel = tittel!!,
        skjemaId = skjemaId!!,
        innsendingsFristDager = innsendingsfristDager,
        språk = Språk.valueOf(forsendelse.språk.uppercase()),
        vedleggsliste =
            vedleggsliste.sortedBy { it.opprettetTidspunkt }.map { vedlegg ->
                OpprettEttersendingsoppgaveVedleggDto(
                    vedleggsnr = if (vedlegg.skjemaId!!.startsWith(vedleggskodeAnnet)) vedleggskodeAnnet else vedlegg.skjemaId!!,
                    tittel = vedlegg.tittel,
                    url = vedleggLenkeMap[vedlegg.skjemaId!!],
                )
            },
    )
