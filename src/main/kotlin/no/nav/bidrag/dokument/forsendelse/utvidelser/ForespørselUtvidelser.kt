package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.model.validerErSann
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import java.time.LocalDateTime

val List<OppdaterDokumentForespørsel>.dokumenterIkkeSlettet get() = this.filter { it.fjernTilknytning == false }
fun OppdaterForsendelseForespørsel.hentDokument(dokumentreferanse: String?) = dokumenter.find { it.dokumentreferanse == dokumentreferanse }

internal fun List<OpprettDokumentForespørsel>.harFlereDokumenterMedSammeJournalpostIdOgReferanse(dokument: DokumentForespørsel) = this
    .filter { it.journalpostId.isNotNullOrEmpty() || it.dokumentreferanse.isNotNullOrEmpty() }
    .filter { it.journalpostId == dokument.journalpostId || it.arkivsystem == dokument.arkivsystem }
    .filter { it.dokumentreferanse.isNotNullOrEmpty() && dokument.dokumentreferanse.isNotNullOrEmpty() && it.dokumentreferanse == dokument.dokumentreferanse }.size > 1

fun List<OpprettDokumentForespørsel>.harNotat(dokumentmalDetaljer: Map<String, DokumentMalDetaljer>) =
    this.any { dokumentmalDetaljer[it.dokumentmalId]?.type == DokumentMalType.NOTAT }

fun OppdaterForsendelseForespørsel.skalDokumentSlettes(dokumentreferanse: String): Boolean =
    this.dokumenter.find { it.dokumentreferanse == dokumentreferanse }?.fjernTilknytning == true

fun OppdaterForsendelseForespørsel.validerGyldigEndring(eksisterendeForsendelse: Forsendelse) {
    val feilmeldinger = mutableListOf<String>()
    if (dokumenter.isNotEmpty()) {
        val forsendelseDokumentreferanse = eksisterendeForsendelse.dokumenter.dokumenterIkkeSlettet.map { it.dokumentreferanse }.toSet()
        val forespørselDokumentreferanser = this.dokumenter.map { it.dokumentreferanse }.toSet()
        val forsendelseHarAlleDokumenterSomSkalEndres = forespørselDokumentreferanser.containsAll(forsendelseDokumentreferanse)

        if (!forsendelseHarAlleDokumenterSomSkalEndres) {
            feilmeldinger.add("Alle dokumenter må sendes i forespørsel ved endring")
        }

        val harReferanseTilSammeDokument =
            eksisterendeForsendelse.dokumenter.dokumenterIkkeSlettet
                .any { forsendelseDok ->

                    this.dokumenter.filter { !forespørselDokumentreferanser.contains(it.dokumentreferanse) }
                        .any { it.journalpostId == forsendelseDok.journalpostIdOriginal || it.dokumentreferanse == forsendelseDok.dokumentreferanseOriginal }
                }
        if (harReferanseTilSammeDokument) {
            feilmeldinger.add("Kan ikke legge til flere dokumenter som peker til samme dokument")
        }
    }

    feilmeldinger.validerErSann(
        this.dokumentDato == null || !this.dokumentDato.isAfter(LocalDateTime.now()),
        "Dokumentdato kan ikke bli satt til fram i tid"
    )

    if (eksisterendeForsendelse.status != ForsendelseStatus.UNDER_OPPRETTELSE) {
        if (mottaker != null) feilmeldinger.add("Kan bare oppdatere mottaker hvis status er ${ForsendelseStatus.UNDER_OPPRETTELSE.name}")
        if (gjelderIdent != null) feilmeldinger.add("Kan bare oppdatere gjelder ident hvis status er ${ForsendelseStatus.UNDER_OPPRETTELSE.name}")
        if (språk != null) feilmeldinger.add("Kan bare oppdatere språk hvis status er ${ForsendelseStatus.UNDER_OPPRETTELSE.name}")
        if (tema != null) feilmeldinger.add("Kan bare oppdatere tema hvis status er ${ForsendelseStatus.UNDER_OPPRETTELSE.name}")
    }

    if (feilmeldinger.isNotEmpty()) {
        throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
    }
}


fun OpprettForsendelseForespørsel.tilBehandlingInfo() = this.behandlingInfo?.let {
    BehandlingInfo(
        behandlingId = it.behandlingId,
        vedtakId = it.vedtakId,
        soknadId = it.soknadId,
        engangsBelopType = it.engangsBelopType,
        vedtakType = it.vedtakType,
        stonadType = it.stonadType,
        behandlingType = if (it.stonadType == null && it.engangsBelopType == null) it.behandlingType else null,
        erVedtakIkkeTilbakekreving = it.erVedtakIkkeTilbakekreving,
        soknadFra = it.soknadFra,
        erFattetBeregnet = it.erFattetBeregnet
    )
}