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

fun Ettersendingsoppgave.tilDto() =
    OpprettEttersendingsppgaveDto(
        tittel = tittel!!,
        skjemaId = skjemaId!!,
        innsendingsFristDager = innsendingsfristDager,
        språk = Språk.valueOf(forsendelse.språk.uppercase()),
        vedleggsliste =
            vedleggsliste.sortedBy { it.opprettetTidspunkt }.map { vedlegg ->
                OpprettEttersendingsoppgaveVedleggDto(
                    vedleggsnr = vedlegg.skjemaId!!,
                    tittel = vedlegg.tittel,
                )
            },
    )
