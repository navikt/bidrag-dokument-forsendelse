package no.nav.bidrag.dokument.forsendelse.service.validering

import no.nav.bidrag.dokument.dto.Fagomrade
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.harArkivPrefiks
import no.nav.bidrag.dokument.forsendelse.model.KanIkkeFerdigstilleForsendelse
import no.nav.bidrag.dokument.forsendelse.model.UgyldigEndringAvForsendelse
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.inneholderKontrollTegn
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.model.validerErSann
import no.nav.bidrag.dokument.forsendelse.model.validerIkkeNullEllerTom
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import no.nav.bidrag.dokument.forsendelse.utvidelser.erNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.harFlereDokumenterMedSammeJournalpostIdOgReferanse
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import java.time.LocalDateTime

object ForespørselValidering {
    fun OpprettForsendelseForespørsel.valider(forsendelseType: ForsendelseType) {
        val feilmeldinger = mutableListOf<String>()

        if (forsendelseType == ForsendelseType.NOTAT && this.dokumenter.size > 1) {
            val dokumentmaler = this.dokumenter.map { it.dokumentmalId }.joinToString(",")
            feilmeldinger.add("Kan ikke opprette ny forsendelse med flere dokumenter hvis forsendelsetype er Notat. dokumentmaler=$dokumentmaler")
        }

        val harFlereDokumenterMedSammeReferanse = this.dokumenter.any {
            this.dokumenter.harFlereDokumenterMedSammeJournalpostIdOgReferanse(it)
        }

        if (harFlereDokumenterMedSammeReferanse) {
            feilmeldinger.add("Kan ikke lagre flere dokumenter med samme journalpostid/arkivsystem og dokumentreferanse")
        }

        if (gjelderIdent.isEmpty()) {
            feilmeldinger.add("Kan ikke opprette forsendelse uten gjelder")
        }

        if (forsendelseType == ForsendelseType.UTGÅENDE && mottaker?.ident.isNullOrEmpty() && mottaker?.navn.isNullOrEmpty()) {
            feilmeldinger.add("Kan ikke opprette forsendelse uten mottaker ident eller navn")
        }
        this.dokumenter.forEachIndexed { i, it ->
            feilmeldinger.addAll(it.valider(i, false))
        }

        if (feilmeldinger.isNotEmpty()) {
            throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
        }
    }

    fun OppdaterDokumentForespørsel.valider(forsendelse: Forsendelse, dokumentreferanse: String) {
        val feilmeldinger: MutableList<String> = mutableListOf()
        feilmeldinger.validerErSann(
            this.tittel == null || this.tittel.isNotEmpty(),
            "Tittel på dokument kan ikke være tom"
        )
        feilmeldinger.validerErSann(
            this.tittel != null && this.tittel.length < 500,
            "Tittel på dokument kan ikke være lengre enn 500 tegn (tittel har lengde på ${this.tittel?.length} tegn)"
        )
        feilmeldinger.validerErSann(
            this.dokumentreferanse == null || this.dokumentreferanse == dokumentreferanse,
            "Dokumentreferanse $dokumentreferanse i forespørsel stemmer ikke med dokumentreferanse i inneholdet på forespørsel ${this.dokumentreferanse}"
        )
        feilmeldinger.validerErSann(
            forsendelse.dokumenter.hentDokument(dokumentreferanse) != null,
            "Forsendelse ${forsendelse.forsendelseId} har ingen dokument med dokumentreferanse $dokumentreferanse"
        )

        feilmeldinger.validerErSann(
            this.dokumentDato == null || !this.dokumentDato.isAfter(LocalDateTime.now()),
            "Dokumentdato kan ikke bli satt til fram i tid"
        )
        if (feilmeldinger.isNotEmpty()) {
            throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
        }
    }

    fun OpprettDokumentForespørsel.valider(
        index: Int? = null,
        throwWhenInvalid: Boolean = true
    ): List<String> {
        val feilmeldinger: MutableList<String> = mutableListOf()
        feilmeldinger.validerIkkeNullEllerTom(
            this.tittel,
            "Tittel på dokument ${index ?: ""} kan ikke være tom".replace("  ", "")
        )
        feilmeldinger.validerErSann(
            this.tittel.length < 500,
            "Tittel på dokument ${index ?: ""} kan ikke være lengre enn 500 tegn (tittel har lengde på ${this.tittel.length} tegn)"
        )
        if (this.dokumentreferanse.isNotNullOrEmpty() || this.journalpostId.isNotNullOrEmpty()) {
            feilmeldinger.validerErSann(
                this.journalpostId.isNotNullOrEmpty() || this.dokumentreferanse.isNotNullOrEmpty() && this.journalpostId.isNotNullOrEmpty(),
                "Både journalpostId og dokumentreferanse må settes hvis dokumentereferanse er satt dokumentreferanse=${this.dokumentreferanse}."
            )
            feilmeldinger.validerErSann(
                !this.journalpostId.isNullOrEmpty() && (this.journalpostId.harArkivPrefiks || this.arkivsystem != null),
                "JournalpostId må innholde arkiv prefiks eller arkivsystem må være satt"
            )
        } else {
            feilmeldinger.validerIkkeNullEllerTom(
                this.dokumentmalId,
                "dokumentmalId må settes hvis dokumentreferanse eller journalpostId ikke er satt"
            )
        }

        if (this.dokumentDato != null && this.dokumentDato.isAfter(LocalDateTime.now())) {
            feilmeldinger.add("Dokumentdato kan ikke være senere enn dagens dato")
        }

        if (feilmeldinger.isNotEmpty() && throwWhenInvalid) {
            throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
        }

        return feilmeldinger
    }

    fun OpprettDokumentForespørsel.validerKanLeggeTilDokument(forsendelse: Forsendelse) {
        val feilmeldinger: MutableList<String> = mutableListOf()

        val forsendelseId = forsendelse.forsendelseId

        feilmeldinger.addAll(this.valider(0, false))

        if (forsendelse.erNotat) {
            feilmeldinger.add("Kan ikke legge til flere dokumenter til et notat")
        }

        if (forsendelse.dokumenter.hentDokument(this.dokumentreferanse) != null) {
            feilmeldinger.add("Forsendelse $forsendelseId har allerede tilknyttet dokument med dokumentreferanse ${this.dokumentreferanse}")
        }

        if (feilmeldinger.isNotEmpty()) {
            throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
        }
    }

    fun Forsendelse.validerKanEndreForsendelse() {
        if (this.status != ForsendelseStatus.UNDER_PRODUKSJON && this.status != ForsendelseStatus.UNDER_OPPRETTELSE) {
            throw UgyldigEndringAvForsendelse("Forsendelse med forsendelseId=${this.forsendelseId} og status ${this.status} kan ikke endres")
        }
    }

    fun Forsendelse.validerKanEndreTilFagområde(nyTema: String) {
        val erGyldigTema = listOf(Fagomrade.BIDRAG.uppercase(), Fagomrade.FARSKAP.uppercase()).contains(nyTema.uppercase())
        if (!erGyldigTema) {
            throw UgyldigEndringAvForsendelse("Forsendelse med forsendelseId=${this.forsendelseId} kan ikke endres til tema $nyTema. $nyTema er ikke en gyldig Bidrag tema.")
        }
    }

    fun Forsendelse.validerKanFerdigstilleForsendelse() {
        if (this.status == ForsendelseStatus.FERDIGSTILT) {
            throw KanIkkeFerdigstilleForsendelse("Forsendelse med forsendelseId=${this.forsendelseId} er allerede ferdigstillt")
        }

        val feilmeldinger = mutableListOf<String>()

        if (this.forsendelseType == ForsendelseType.UTGÅENDE && this.mottaker == null) {
            feilmeldinger.add("Forsendelse med type ${this.forsendelseType} mangler mottaker")
        }

        if (this.dokumenter.isEmpty()) {
            feilmeldinger.add("Forsendelse mangler dokument")
        }

        if (!this.dokumenter.erAlleFerdigstilt) {
            feilmeldinger.add("En eller flere dokumenter i forsendelsen er ikke ferdigstilt.")
        }

        this.dokumenter.forEach {
            if (it.tittel.inneholderKontrollTegn()) {
                feilmeldinger.add("Dokument med tittel ${it.tittel} og dokumentreferanse ${it.dokumentreferanse} i forsendelse ${this.forsendelseId} inneholder ugyldig tegn")
            }
        }

        if (feilmeldinger.isNotEmpty()) {
            throw KanIkkeFerdigstilleForsendelse(feilmeldinger.joinToString(","))
        }
    }
}
