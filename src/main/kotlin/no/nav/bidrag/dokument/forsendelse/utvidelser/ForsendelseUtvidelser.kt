package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType

fun List<Dokument>.hentDokument(dokumentreferanse: String?) = dokumenterIkkeSlettet.find { it.dokumentreferanse == dokumentreferanse }
val List<Dokument>.erAlleFerdigstilt get() = dokumenterIkkeSlettet.all { it.dokumentStatus == DokumentStatus.FERDIGSTILT }
val List<Dokument>.ikkeSlettetSortertEtterRekkefølge get() = dokumenterIkkeSlettet.sortedBy { it.rekkefølgeIndeks }
val List<Dokument>.hoveddokument get() = dokumenterIkkeSlettet.find { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.vedlegger get() = dokumenterIkkeSlettet.filter { it.tilknyttetSom == DokumentTilknyttetSom.VEDLEGG }.sortedBy { it.rekkefølgeIndeks }
val List<Dokument>.dokumenterIkkeSlettet get() = this.filter { it.slettetTidspunkt == null }
val List<Dokument>.dokumenterSlettet get() = this.filter { it.slettetTidspunkt != null }
val List<Dokument>.harHoveddokument get() = dokumenterIkkeSlettet.any { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.sortertEtterRekkefølge get(): List<Dokument> {
    val dokumenterIkkeSlettet = this.dokumenterIkkeSlettet.sortedBy { it.rekkefølgeIndeks }.mapIndexed { i, it ->
            it.copy(
                rekkefølgeIndeks = i,
                tilknyttetSom = if (i == 0) DokumentTilknyttetSom.HOVEDDOKUMENT else DokumentTilknyttetSom.VEDLEGG
            )
    }

    var sisteIndeks = dokumenterIkkeSlettet.size - 1
    val dokumenterSlettet = this.dokumenterSlettet.map {
        sisteIndeks++
        it.copy(
            rekkefølgeIndeks = sisteIndeks,
            tilknyttetSom = DokumentTilknyttetSom.VEDLEGG
        )
    }
    return dokumenterIkkeSlettet + dokumenterSlettet
}

val Forsendelse.erNotat get() = forsendelseType == ForsendelseType.NOTAT
val Forsendelse.forsendelseIdMedPrefix get() = "BIF-$forsendelseId"
val Dokument.journalpostIdMedPrefix
    get() = if (journalpostId.isNullOrEmpty())
        this.forsendelse.forsendelseIdMedPrefix else when (arkivsystem) {
        DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> "BID-$journalpostId"
        DokumentArkivSystem.JOARK -> "JOARK-$journalpostId"
        else -> this.forsendelse.forsendelseIdMedPrefix
    }
