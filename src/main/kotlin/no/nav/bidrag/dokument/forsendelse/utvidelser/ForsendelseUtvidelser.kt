package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.behandling.felles.dto.vedtak.VedtakDto
import no.nav.bidrag.dokument.forsendelse.consumer.dto.BehandlingDto
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.model.kanIkkeDistribuereForsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.Rolletype
import no.nav.bidrag.domain.enums.StonadType
import no.nav.bidrag.domain.enums.VedtakType

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
        Rolletype.BM -> "Bidragsmottaker"
        Rolletype.BP -> "Bidragspliktig"
        Rolletype.BA -> "Barn"
        Rolletype.RM -> "Reel mottaker"
        else -> null
    }
}

fun BehandlingDto.tilStonadtype(): StonadType? {
    return try {
        behandlingType.let { StonadType.valueOf(it) }
    } catch (e: Exception) {
        null
    }
}

fun BehandlingDto.tilEngangsbelopType(): EngangsbelopType? {
    return try {
        behandlingType.let { EngangsbelopType.valueOf(it) }
    } catch (e: Exception) {
        null
    }
}

fun BehandlingInfo.tilBeskrivelseBehandlingType(vedtak: VedtakDto? = null, behandling: BehandlingDto? = null): String? {
    val stonadTypeValue =
        vedtak?.stonadsendringListe?.isNotEmpty()?.ifTrue { vedtak.stonadsendringListe[0].type } ?: behandling?.tilStonadtype() ?: stonadType
    val engangsBelopTypeValue =
        vedtak?.engangsbelopListe?.isNotEmpty()?.ifTrue { vedtak.engangsbelopListe[0].type } ?: behandling?.tilEngangsbelopType() ?: engangsBelopType
    return when (stonadTypeValue) {
        StonadType.FORSKUDD -> "Forskudd"
        StonadType.BIDRAG -> "Bidrag"
        StonadType.BIDRAG18AAR -> "Bidrag 18 år"
        StonadType.EKTEFELLEBIDRAG -> "Ektefellebidrag"
        StonadType.OPPFOSTRINGSBIDRAG -> "Oppfostringbidrag"
        StonadType.MOTREGNING -> "Motregning"
        else -> when (engangsBelopTypeValue) {
            EngangsbelopType.SAERTILSKUDD -> "Særtilskudd"
            EngangsbelopType.DIREKTE_OPPGJOR -> "Direkte oppgjør"
            EngangsbelopType.ETTERGIVELSE -> "Ettergivelse"
            EngangsbelopType.ETTERGIVELSE_TILBAKEKREVING -> "Ettergivelse tilbakekreving"
            EngangsbelopType.GEBYR_MOTTAKER -> "Gebyr"
            EngangsbelopType.GEBYR_SKYLDNER -> "Gebyr"
            EngangsbelopType.TILBAKEKREVING -> "Tilbakekreving"
            else -> behandlingType?.lowercase()?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
        }
    }
}

fun BehandlingInfo.gjelderKlage(vedtak: VedtakDto? = null, behandling: BehandlingDto? = null) =
    vedtak?.type == VedtakType.KLAGE || behandling?.soknadType == VedtakType.KLAGE || vedtakType == VedtakType.KLAGE

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
            if (gjelderKlage) stringBuilder.add("om mottatt klage på vedtak om ${behandlingType.lowercase()}")
            else stringBuilder.add("om ${behandlingType.lowercase()}")
        }
    }

    if (rolle != null) {
        stringBuilder.add("til ${rolle.toName()?.lowercase()}")
    }
    return stringBuilder.joinToString(" ")
}
