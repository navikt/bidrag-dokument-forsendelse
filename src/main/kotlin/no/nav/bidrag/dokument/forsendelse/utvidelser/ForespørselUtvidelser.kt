package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty

fun OppdaterForsendelseForespørsel.skalDokumentSlettes(dokumentreferanse: String?) = dokumenter.any { it.fjernTilknytning && it.dokumentreferanse == dokumentreferanse}
fun OppdaterForsendelseForespørsel.hentDokument(dokumentreferanse: String?) = dokumenter.find { it.dokumentreferanse == dokumentreferanse }

internal fun List<OpprettDokumentForespørsel>.harFlereDokumenterMedSammeJournalpostIdOgReferanse(dokument: DokumentForespørsel) = this
    .filter { it.journalpostId.isNotNullOrEmpty() || it.dokumentreferanse.isNotNullOrEmpty()}
    .filter { it.journalpostId == dokument.journalpostId || it.arkivsystem == dokument.arkivsystem }
    .filter { it.dokumentreferanse == dokument.dokumentreferanse }.size > 1

fun List<OpprettDokumentForespørsel>.hentHoveddokument() = this[0]
fun List<OpprettDokumentForespørsel>.harNotat(dokumentmalDetaljer: Map<String, DokumentMalDetaljer>) = this.any { dokumentmalDetaljer[it.dokumentmalId]?.type == DokumentMalType.NOTAT }

fun OppdaterForsendelseForespørsel.validerGyldigEndring(eksisterendeForsendelse: Forsendelse) {
    val feilmeldinger = mutableListOf<String>()
    val forsendelseDokumentreferanse = eksisterendeForsendelse.dokumenter.map { it.dokumentreferanse }.toSet()
    val forespørselDokumentreferanser = this.dokumenter.map { it.dokumentreferanse }.toSet()
    val forsendelseHarAlleDokumenterSomSkalEndres = forespørselDokumentreferanser.containsAll(forsendelseDokumentreferanse)
    val harSammeAntallDokumenter = eksisterendeForsendelse.dokumenter.size == this.dokumenter.size

    if (!harSammeAntallDokumenter || !forsendelseHarAlleDokumenterSomSkalEndres){
        feilmeldinger.add("Alle dokumenter må sendes i forespørsel ved endring")
    }

    if (feilmeldinger.isNotEmpty()){
        throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
    }
}