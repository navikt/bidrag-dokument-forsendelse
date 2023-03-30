package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom

val Dokument.forsendelseIdMedPrefix get() = this.forsendelse.forsendelseIdMedPrefix
fun List<Dokument>.hentDokument(dokumentreferanse: String?) = dokumenterIkkeSlettet.find { it.dokumentreferanse == dokumentreferanse }
val List<Dokument>.erAlleFerdigstilt get() = dokumenterIkkeSlettet.isNotEmpty() && dokumenterIkkeSlettet.all { it.dokumentStatus == DokumentStatus.FERDIGSTILT }
val List<Dokument>.ikkeSlettetSortertEtterRekkefølge get() = dokumenterIkkeSlettet.sortedBy { it.rekkefølgeIndeks }
val List<Dokument>.hoveddokument get() = dokumenterIkkeSlettet.find { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.vedlegger
    get() = dokumenterIkkeSlettet.filter { it.tilknyttetSom == DokumentTilknyttetSom.VEDLEGG }.sortedBy { it.rekkefølgeIndeks }
val List<Dokument>.dokumenterIkkeSlettet get() = this.filter { it.slettetTidspunkt == null }
val List<Dokument>.dokumenterLogiskSlettet get() = this.filter { it.slettetTidspunkt != null }
val List<Dokument>.sortertEtterRekkefølge
    get(): List<Dokument> {
        val dokumenterIkkeSlettet = this.dokumenterIkkeSlettet.sortedBy { it.rekkefølgeIndeks }.mapIndexed { i, it ->
            it.copy(
                rekkefølgeIndeks = i
            )
        }

        var sisteIndeks = dokumenterIkkeSlettet.size - 1
        val dokumenterSlettet = this.dokumenterLogiskSlettet.map {
            sisteIndeks++
            it.copy(
                rekkefølgeIndeks = sisteIndeks
            )
        }
        return dokumenterIkkeSlettet + dokumenterSlettet
    }
