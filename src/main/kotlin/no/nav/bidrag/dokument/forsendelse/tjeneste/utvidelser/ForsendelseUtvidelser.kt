package no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser

import no.nav.bidrag.dokument.dto.AktorDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.DokumentDto
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.KodeDto
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.utenPrefiks
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.model.KanIkkeFerdigstilleForsendelse
import no.nav.bidrag.dokument.forsendelse.model.UgyldigEndringAvForsendelse

fun Dokument.tilDokumentStatusDto() = when (dokumentStatus) {
    DokumentStatus.BESTILLING_FEILET -> DokumentStatusDto.BESTILLING_FEILET
    DokumentStatus.UNDER_REDIGERING -> DokumentStatusDto.UNDER_REDIGERING
    DokumentStatus.UNDER_PRODUKSJON -> DokumentStatusDto.UNDER_PRODUKSJON
    DokumentStatus.FERDIGSTILT -> DokumentStatusDto.FERDIGSTILT
    DokumentStatus.IKKE_BESTILT -> DokumentStatusDto.IKKE_BESTILT
    DokumentStatus.AVBRUTT -> DokumentStatusDto.AVBRUTT
}

fun Dokument.tilArkivSystemDto() = when (arkivsystem) {
    DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
    DokumentArkivSystem.JOARK -> DokumentArkivSystemDto.JOARK
    else -> DokumentArkivSystemDto.UKJENT
}
val Dokument.journalpostIdMedPrefix get() = if (journalpostId.isNullOrEmpty())
    "BIF-${this.forsendelse.forsendelseId}" else when(arkivsystem){
    DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> "BID-$journalpostId"
    DokumentArkivSystem.JOARK -> "JOARK-$journalpostId"
    else -> "BIF-${this.forsendelse.forsendelseId}"
}

fun List<Dokument>.hent(dokumentreferanse: String?) = dokumenterIkkeSlettet.find { it.dokumentreferanse == dokumentreferanse }
val List<Dokument>.erAlleFerdigstilt get() = dokumenterIkkeSlettet.all { it.dokumentStatus == DokumentStatus.FERDIGSTILT }
val List<Dokument>.hoveddokumentFørst get() = dokumenterIkkeSlettet.sortedByDescending { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.hoveddokument get() = dokumenterIkkeSlettet.find { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.vedlegger get() = dokumenterIkkeSlettet.filter { it.tilknyttetSom == DokumentTilknyttetSom.VEDLEGG }
val List<Dokument>.dokumenterIkkeSlettet get() = this.filter { it.slettetTidspunkt == null }
val List<Dokument>.harHoveddokument get() = dokumenterIkkeSlettet.any { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.alleMedMinstEtHoveddokument get(): List<Dokument> {
      var harHoveddokument = this.harHoveddokument
      return this.mapIndexed { i, it ->
            it.copy(
                    tilknyttetSom = when (harHoveddokument) {
                        true -> it.tilknyttetSom
                        false -> if (it.slettetTidspunkt != null) DokumentTilknyttetSom.VEDLEGG
                        else {
                            harHoveddokument = true
                            DokumentTilknyttetSom.HOVEDDOKUMENT
                        }
                    }
                )
        }
    }


fun Forsendelse.validerKanEndreForsendelse(){
    if (this.status != ForsendelseStatus.UNDER_PRODUKSJON){
        throw UgyldigEndringAvForsendelse("Forsendelse med forsendelseId=${this.forsendelseId} og status ${this.status} kan ikke endres")
    }
}

fun Forsendelse.validerKanFerdigstilleForsendelse(){
    if (this.status == ForsendelseStatus.FERDIGSTILT){
        throw KanIkkeFerdigstilleForsendelse("Forsendelse med forsendelseId=${this.forsendelseId} er allerede ferdigstillt")
    }

    val feilmeldinger = mutableListOf<String>()

    if (this.forsendelseType == ForsendelseType.UTGÅENDE && this.mottaker == null){
        feilmeldinger.add("Forsendelse med type ${this.forsendelseType} mangler mottaker")
    }

    if (this.dokumenter.isEmpty()){
        feilmeldinger.add("Forsendelse mangler dokument")
    }

    if (!this.dokumenter.erAlleFerdigstilt){
        feilmeldinger.add("En eller flere dokumenter i forsendelsen er ikke ferdigstilt.")
    }

    if (feilmeldinger.isNotEmpty()){
        throw KanIkkeFerdigstilleForsendelse(feilmeldinger.joinToString(","))
    }
}

val Forsendelse.erNotat get() = forsendelseType == ForsendelseType.NOTAT

fun Forsendelse.tilJournalpostDto() = JournalpostDto(
    avsenderMottaker = this.mottaker?.let {
        AvsenderMottakerDto(it.navn, it.ident, adresse = it.adresse?.let { adresse -> no.nav.bidrag.dokument.dto.MottakerAdresseTo(
            adresselinje1 = adresse.adresselinje1,
            adresselinje2 = adresse.adresselinje2,
            adresselinje3 = adresse.adresselinje3,
            bruksenhetsnummer = adresse.bruksenhetsnummer,
            poststed = adresse.poststed,
            postnummer = adresse.postnummer,
            landkode = adresse.landkode,
            landkode3 = adresse.landkode3
        )})
    },
    joarkJournalpostId = this.arkivJournalpostId,
    språk = this.språk,
    gjelderIdent = this.gjelderIdent,
    gjelderAktor = AktorDto(this.gjelderIdent),
    brevkode = KodeDto(this.dokumenter.hoveddokument?.dokumentmalId),
    innhold = this.dokumenter.hoveddokument?.tittel,
    fagomrade = "BID",
    dokumentType = when (this.forsendelseType) {
        ForsendelseType.NOTAT -> "X"
        ForsendelseType.UTGÅENDE -> "U"
    },
    journalfortAv = opprettetAvIdent,
    journalstatus = when(this.status){
        ForsendelseStatus.DISTRIBUERT -> "E"
        ForsendelseStatus.AVBRUTT -> "F"
        ForsendelseStatus.FERDIGSTILT -> "FS"
        ForsendelseStatus.UNDER_PRODUKSJON -> if (this.dokumenter.erAlleFerdigstilt)
                    if (this.forsendelseType == ForsendelseType.UTGÅENDE) "KP"
                    else "FS"
                else "D"
    },
    journalpostId = "BIF-${this.forsendelseId}",
    dokumentDato = this.opprettetTidspunkt.toLocalDate(),
    journalfortDato = this.opprettetTidspunkt.toLocalDate(),
    journalforendeEnhet = this.enhet,
    dokumenter = this.dokumenter.hoveddokumentFørst.map {dokument->
        DokumentDto(
            dokumentreferanse = dokument.dokumentreferanse,
            journalpostId = dokument.journalpostId?.let { jpId -> when(dokument.arkivsystem){
               DokumentArkivSystem.JOARK -> "JOARK-${jpId.utenPrefiks}"
               DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> "BID-${jpId.utenPrefiks}"
               else -> null
            }},
            arkivSystem = dokument.tilArkivSystemDto(),
            metadata = dokument.metadata,
            tittel = dokument.tittel,
            dokumentmalId = dokument.dokumentmalId,
            status = dokument.tilDokumentStatusDto()
        )
    },
    sakstilknytninger = listOf(saksnummer),
    opprettetAvIdent = this.opprettetAvIdent
    )

fun Forsendelse.tilForsendelseRespons() = ForsendelseResponsTo(
    mottaker = this.mottaker?.let {
        MottakerTo(
            ident = it.ident,
            språk = it.språk,
            navn = it.navn,
            adresse = it.adresse?.let {adresse->
                MottakerAdresseTo(
                    adresselinje1 = adresse.adresselinje1,
                    adresselinje2 = adresse.adresselinje2,
                    adresselinje3 = adresse.adresselinje3,
                    poststed = adresse.poststed,
                    postnummer = adresse.postnummer,
                    landkode = adresse.landkode,
                )
            }
        )
    },
    tittel = this.dokumenter.hoveddokument?.tittel,
    saksnummer = this.saksnummer,
    forsendelseType = when (this.forsendelseType) {
        ForsendelseType.NOTAT -> ForsendelseTypeTo.NOTAT
        ForsendelseType.UTGÅENDE -> ForsendelseTypeTo.UTGÅENDE
    },
    status = when (this.status) {
        ForsendelseStatus.UNDER_PRODUKSJON -> ForsendelseStatusTo.UNDER_PRODUKSJON
        ForsendelseStatus.DISTRIBUERT -> ForsendelseStatusTo.DISTRIBUERT
        ForsendelseStatus.AVBRUTT -> ForsendelseStatusTo.SLETTET
        ForsendelseStatus.FERDIGSTILT -> ForsendelseStatusTo.FERDIGSTILT
    },
    opprettetDato = this.opprettetTidspunkt.toLocalDate(),
    distribuertDato = this.distribuertTidspunkt?.toLocalDate(),
    enhet = this.enhet,
    opprettetAvIdent = this.opprettetAvIdent,
    opprettetAvNavn = this.opprettetAvNavn,
    dokumenter = this.dokumenter.hoveddokumentFørst.map {
        DokumentRespons(
            dokumentreferanse = it.dokumentreferanse,
            tittel = it.tittel,
            journalpostId = it.journalpostId,
            dokumentmalId = it.dokumentmalId,
            arkivsystem = it.tilArkivSystemDto(),
            metadata = it.metadata,
            status = when (it.dokumentStatus) {
                DokumentStatus.UNDER_REDIGERING -> DokumentStatusTo.UNDER_REDIGERING
                DokumentStatus.UNDER_PRODUKSJON -> DokumentStatusTo.UNDER_PRODUKSJON
                DokumentStatus.FERDIGSTILT -> DokumentStatusTo.FERDIGSTILT
                DokumentStatus.IKKE_BESTILT -> DokumentStatusTo.IKKE_BESTILT
                DokumentStatus.BESTILLING_FEILET -> DokumentStatusTo.BESTILLING_FEILET
                DokumentStatus.AVBRUTT -> DokumentStatusTo.AVBRUTT
            }
        )
    })
