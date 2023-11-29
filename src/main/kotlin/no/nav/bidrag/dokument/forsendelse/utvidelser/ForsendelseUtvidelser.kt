package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.consumer.dto.BehandlingDto
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.model.kanIkkeDistribuereForsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto

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
            "Forsendelse har feil status ${this.status}"
        )
    }
}

fun Forsendelse.kanDistribueres(): Boolean {
    return try {
        validerKanDistribuere()
        true
    } catch (_: Exception) {
        false
    }
}

fun Rolletype.toName(): String? {
    return when (this) {
        Rolletype.BIDRAGSMOTTAKER -> "Bidragsmottaker"
        Rolletype.BIDRAGSPLIKTIG -> "Bidragspliktig"
        Rolletype.BARN -> "Barn"
        Rolletype.REELMOTTAKER -> "Reel mottaker"
        else -> null
    }
}

fun BehandlingDto.tilStønadstype(): Stønadstype? {
    return try {
        behandlingType.let { Stønadstype.valueOf(it) }
    } catch (e: Exception) {
        null
    }
}

fun BehandlingDto.tilEngangsbeløptype(): Engangsbeløptype? {
    return try {
        behandlingType.let { Engangsbeløptype.valueOf(it) }
    } catch (e: Exception) {
        null
    }
}

fun BehandlingInfo.tilBeskrivelseBehandlingType(vedtak: VedtakDto? = null, behandling: BehandlingDto? = null): String? {
    val StønadstypeValue =
        vedtak?.stønadsendringListe?.isNotEmpty()?.ifTrue { vedtak.stønadsendringListe[0].type } ?: behandling?.tilStønadstype() ?: stonadType
    val EngangsbeløptypeValue =
        vedtak?.engangsbeløpListe?.isNotEmpty()?.ifTrue { vedtak.engangsbeløpListe[0].type } ?: behandling?.tilEngangsbeløptype() ?: engangsBelopType
    return when (StønadstypeValue) {
        Stønadstype.FORSKUDD -> "Bidragsforskudd"
        Stønadstype.BIDRAG -> "Barnebidrag"
        Stønadstype.BIDRAG18AAR -> "Barnebidrag 18 år"
        Stønadstype.EKTEFELLEBIDRAG -> "Ektefellebidrag"
        Stønadstype.OPPFOSTRINGSBIDRAG -> "Oppfostringbidrag"
        Stønadstype.MOTREGNING -> "Motregning"
        else -> when (EngangsbeløptypeValue) {
            Engangsbeløptype.SAERTILSKUDD -> "Særtilskudd"
            Engangsbeløptype.DIREKTE_OPPGJOR -> "Direkte oppgjør"
            Engangsbeløptype.ETTERGIVELSE -> "Ettergivelse"
            Engangsbeløptype.ETTERGIVELSE_TILBAKEKREVING -> "Ettergivelse tilbakekreving"
            Engangsbeløptype.GEBYR_MOTTAKER -> "Gebyr"
            Engangsbeløptype.GEBYR_SKYLDNER -> "Gebyr"
            Engangsbeløptype.TILBAKEKREVING -> "Tilbakekreving"
            else -> behandlingType?.lowercase()?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
        }
    }
}

fun BehandlingInfo.gjelderKlage(vedtak: VedtakDto? = null, behandling: BehandlingDto? = null) =
    vedtak?.type == Vedtakstype.KLAGE || behandling?.soknadType == Vedtakstype.KLAGE || vedtakType == Vedtakstype.KLAGE

fun BehandlingInfo.tilBeskrivelse(rolle: Rolletype?, vedtak: VedtakDto? = null, behandling: BehandlingDto? = null): String {
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
