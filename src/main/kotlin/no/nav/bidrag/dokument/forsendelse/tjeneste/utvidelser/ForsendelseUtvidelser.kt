package no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser

import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.DokumentDto
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentArkivSystemTo
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType

fun List<Dokument>.hent(dokumentreferanse: String?) = dokumenterIkkeSlettet.find { it.dokumentreferanse == dokumentreferanse }
val List<Dokument>.erAlleFerdigstilt get() = dokumenterIkkeSlettet.all { it.dokumentStatus == DokumentStatus.FERDIGSTILT }
val List<Dokument>.hoveddokumentFørst get() = dokumenterIkkeSlettet.sortedByDescending { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.hoveddokument get() = dokumenterIkkeSlettet.find { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.dokumenterIkkeSlettet get() = this.filter { it.slettetTidspunkt == null }
val List<Dokument>.harHoveddokument get() = dokumenterIkkeSlettet.any { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val List<Dokument>.alleMedMinstEnHoveddokument
    get() = this.mapIndexed { i, it ->
        it.copy(
            tilknyttetSom = when (this.harHoveddokument) {
                true -> it.tilknyttetSom
                false -> if (it.slettetTidspunkt != null) DokumentTilknyttetSom.VEDLEGG else if (i == 0) DokumentTilknyttetSom.HOVEDDOKUMENT else DokumentTilknyttetSom.VEDLEGG
            }
        )
    }

fun Forsendelse.tilJournalpostDto() = JournalpostDto(
    avsenderMottaker = this.mottaker?.let {
        AvsenderMottakerDto(it.navn, it.ident)
    },
    innhold = this.dokumenter.hoveddokument?.tittel,
    fagomrade = "BID",
    dokumentType = when (this.forsendelseType) {
        ForsendelseType.NOTAT -> "X"
        ForsendelseType.UTGÅENDE -> "U"
    },
    journalstatus = if (this.dokumenter.erAlleFerdigstilt) "J" else "D",
    journalpostId = "BIF-${this.forsendelseId}",
    dokumentDato = this.opprettetTidspunkt.toLocalDate(),
    journalfortDato = this.opprettetTidspunkt.toLocalDate(),
    journalforendeEnhet = this.enhet,
    dokumenter = this.dokumenter.hoveddokumentFørst.map {
        DokumentDto(
            dokumentreferanse = it.dokumentreferanse,
            tittel = it.tittel
        )
    })

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
            arkivsystem = when (it.arkivsystem) {
                DokumentArkivSystem.BREVSERVER -> DokumentArkivSystemTo.BREVSERVER
                DokumentArkivSystem.JOARK -> DokumentArkivSystemTo.JOARK
                else -> null
            },
            status = when (it.dokumentStatus) {
                DokumentStatus.UNDER_REDIGERING -> DokumentStatusTo.UNDER_REDIGERING
                DokumentStatus.UNDER_PRODUKSJON -> DokumentStatusTo.UNDER_PRODUKSJON
                DokumentStatus.FERDIGSTILT -> DokumentStatusTo.FERDIGSTILT
                DokumentStatus.IKKE_BESTILT -> DokumentStatusTo.IKKE_BESTILT
                DokumentStatus.AVBRUTT -> null
            }
        )
    })