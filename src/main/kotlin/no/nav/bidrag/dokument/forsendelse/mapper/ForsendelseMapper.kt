package no.nav.bidrag.dokument.forsendelse.mapper

import no.nav.bidrag.dokument.dto.AktorDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.DokumentDto
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.Kanal
import no.nav.bidrag.dokument.dto.KodeDto
import no.nav.bidrag.dokument.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.utenPrefiks
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge

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

fun Forsendelse.tilJournalpostDto() = JournalpostDto(
    avsenderMottaker = this.mottaker?.let {
        AvsenderMottakerDto(it.navn, it.ident, adresse = it.adresse?.let { adresse ->
            MottakerAdresseTo(
                adresselinje1 = adresse.adresselinje1,
                adresselinje2 = adresse.adresselinje2,
                adresselinje3 = adresse.adresselinje3,
                bruksenhetsnummer = adresse.bruksenhetsnummer,
                poststed = adresse.poststed,
                postnummer = adresse.postnummer,
                landkode = adresse.landkode,
                landkode3 = adresse.landkode3
            )
        })
    },
    joarkJournalpostId = this.fagarkivJournalpostId,
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
    journalstatus = when (this.status) {
        ForsendelseStatus.DISTRIBUERT_LOKALT, ForsendelseStatus.DISTRIBUERT -> "E"
        ForsendelseStatus.AVBRUTT -> "F"
        ForsendelseStatus.FERDIGSTILT -> "FS"
        ForsendelseStatus.UNDER_PRODUKSJON -> if (this.dokumenter.erAlleFerdigstilt)
            if (this.forsendelseType == ForsendelseType.UTGÅENDE) "KP"
            else "FS"
        else "D"
    },
    journalpostId = forsendelseIdMedPrefix,
    dokumentDato = this.opprettetTidspunkt.toLocalDate(),
    journalfortDato = this.opprettetTidspunkt.toLocalDate(),
    journalforendeEnhet = this.enhet,
    feilfort = status == ForsendelseStatus.AVBRUTT,
    dokumenter = this.dokumenter.ikkeSlettetSortertEtterRekkefølge.map { dokument ->
        DokumentDto(
            dokumentreferanse = dokument.dokumentreferanse,
            journalpostId = dokument.journalpostId?.let { jpId ->
                when (dokument.arkivsystem) {
                    DokumentArkivSystem.JOARK -> "JOARK-${jpId.utenPrefiks}"
                    DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> "BID-${jpId.utenPrefiks}"
                    else -> null
                }
            },
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
            adresse = it.adresse?.let { adresse ->
                no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo(
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
        ForsendelseStatus.DISTRIBUERT_LOKALT -> ForsendelseStatusTo.DISTRIBUERT_LOKALT
        ForsendelseStatus.AVBRUTT -> ForsendelseStatusTo.SLETTET
        ForsendelseStatus.FERDIGSTILT -> ForsendelseStatusTo.FERDIGSTILT
    },
    opprettetDato = this.opprettetTidspunkt.toLocalDate(),
    distribuertDato = this.distribuertTidspunkt?.toLocalDate(),
    enhet = this.enhet,
    opprettetAvIdent = this.opprettetAvIdent,
    opprettetAvNavn = this.opprettetAvNavn,
    dokumenter = this.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
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

