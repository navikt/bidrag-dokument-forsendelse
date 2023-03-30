package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.model.kanIkkeDistribuereForsendelse

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
