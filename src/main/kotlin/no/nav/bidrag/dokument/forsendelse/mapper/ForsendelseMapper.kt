package no.nav.bidrag.dokument.forsendelse.mapper

import no.nav.bidrag.dokument.dto.AktorDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.AvsenderMottakerDtoIdType
import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.dto.DokumentDto
import no.nav.bidrag.dokument.dto.DokumentStatusDto
import no.nav.bidrag.dokument.dto.DokumentType
import no.nav.bidrag.dokument.dto.Fagomrade
import no.nav.bidrag.dokument.dto.JournalpostDto
import no.nav.bidrag.dokument.dto.Journalstatus
import no.nav.bidrag.dokument.dto.KodeDto
import no.nav.bidrag.dokument.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseResponsTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.model.alpha3LandkodeTilAlpha2
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumentDato
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import no.nav.bidrag.dokument.forsendelse.utvidelser.erNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.erUtgående
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge

fun Dokument.tilDokumentStatusDto() = when (dokumentStatus) {
    DokumentStatus.MÅ_KONTROLLERES -> DokumentStatusDto.UNDER_REDIGERING
    DokumentStatus.KONTROLLERT -> DokumentStatusDto.FERDIGSTILT
    DokumentStatus.BESTILLING_FEILET -> DokumentStatusDto.BESTILLING_FEILET
    DokumentStatus.UNDER_REDIGERING -> DokumentStatusDto.UNDER_REDIGERING
    DokumentStatus.UNDER_PRODUKSJON -> DokumentStatusDto.UNDER_PRODUKSJON
    DokumentStatus.FERDIGSTILT -> DokumentStatusDto.FERDIGSTILT
    DokumentStatus.IKKE_BESTILT -> DokumentStatusDto.IKKE_BESTILT
    DokumentStatus.AVBRUTT -> DokumentStatusDto.AVBRUTT
}

fun Dokument.tilDokumentStatusTo() = when (dokumentStatus) {
    DokumentStatus.MÅ_KONTROLLERES -> DokumentStatusTo.MÅ_KONTROLLERES
    DokumentStatus.KONTROLLERT -> DokumentStatusTo.KONTROLLERT
    DokumentStatus.UNDER_REDIGERING -> DokumentStatusTo.UNDER_REDIGERING
    DokumentStatus.UNDER_PRODUKSJON -> DokumentStatusTo.UNDER_PRODUKSJON
    DokumentStatus.FERDIGSTILT -> DokumentStatusTo.FERDIGSTILT
    DokumentStatus.IKKE_BESTILT -> DokumentStatusTo.IKKE_BESTILT
    DokumentStatus.BESTILLING_FEILET -> DokumentStatusTo.BESTILLING_FEILET
    DokumentStatus.AVBRUTT -> DokumentStatusTo.AVBRUTT
}

fun Dokument.tilArkivSystemDto() = when (arkivsystem) {
    DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
    DokumentArkivSystem.JOARK -> DokumentArkivSystemDto.JOARK
    else -> DokumentArkivSystemDto.UKJENT
}

fun Forsendelse.tilJournalpostDto() = JournalpostDto(
    avsenderMottaker = this.mottaker?.let {
        AvsenderMottakerDto(
            navn = it.navn,
            ident = it.ident,
            type = when (it.identType) {
                MottakerIdentType.SAMHANDLER -> AvsenderMottakerDtoIdType.SAMHANDLER
                else -> AvsenderMottakerDtoIdType.FNR
            },
            adresse = it.adresse?.let { adresse ->
                MottakerAdresseTo(
                    adresselinje1 = adresse.adresselinje1,
                    adresselinje2 = adresse.adresselinje2,
                    adresselinje3 = adresse.adresselinje3,
                    bruksenhetsnummer = adresse.bruksenhetsnummer,
                    poststed = adresse.poststed,
                    postnummer = adresse.postnummer,
                    landkode = adresse.landkode ?: alpha3LandkodeTilAlpha2(adresse.landkode3),
                    landkode3 = adresse.landkode3
                )
            }
        )
    },
    joarkJournalpostId = this.journalpostIdFagarkiv,
    språk = this.språk,
    gjelderIdent = this.gjelderIdent,
    gjelderAktor = AktorDto(this.gjelderIdent),
    brevkode = KodeDto(this.dokumenter.hoveddokument?.dokumentmalId),
    innhold = this.dokumenter.hoveddokument?.tittel ?: "Forsendelse $forsendelseId",
    fagomrade = when (tema) {
        ForsendelseTema.FAR -> Fagomrade.FARSKAP
        else -> Fagomrade.BIDRAG
    },
    dokumentType = when (this.forsendelseType) {
        ForsendelseType.NOTAT -> DokumentType.NOTAT
        ForsendelseType.UTGÅENDE -> DokumentType.UTGÅENDE
    },
    journalfortAv = if (opprettetAvIdent == "bisys") "Bisys (Automatisk jobb)" else opprettetAvIdent,
    journalstatus = when (this.status) {
        ForsendelseStatus.DISTRIBUERT_LOKALT, ForsendelseStatus.DISTRIBUERT -> Journalstatus.EKSPEDERT
        ForsendelseStatus.SLETTET -> Journalstatus.UTGAR
        ForsendelseStatus.AVBRUTT -> Journalstatus.FEILREGISTRERT
        ForsendelseStatus.FERDIGSTILT -> if (erUtgående) Journalstatus.KLAR_TIL_PRINT else Journalstatus.FERDIGSTILT
        ForsendelseStatus.UNDER_PRODUKSJON -> if (this.dokumenter.erAlleFerdigstilt) {
            if (erUtgående) Journalstatus.KLAR_TIL_PRINT else Journalstatus.FERDIGSTILT
        } else {
            Journalstatus.UNDER_PRODUKSJON
        }
    },
    journalpostId = forsendelseIdMedPrefix,
    dokumentDato = if (erNotat) this.dokumentDato?.toLocalDate() ?: this.opprettetTidspunkt.toLocalDate() else this.opprettetTidspunkt.toLocalDate(),
    journalfortDato = this.opprettetTidspunkt.toLocalDate(),
    journalforendeEnhet = this.enhet,
    feilfort = status == ForsendelseStatus.AVBRUTT,
    dokumenter = this.dokumenter.ikkeSlettetSortertEtterRekkefølge.map { dokument ->
        DokumentDto(
            dokumentreferanse = dokument.dokumentreferanse,
            journalpostId = dokument.journalpostId,
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

fun Forsendelse.tilForsendelseStatusTo() = when (this.status) {
    ForsendelseStatus.UNDER_PRODUKSJON -> ForsendelseStatusTo.UNDER_PRODUKSJON
    ForsendelseStatus.DISTRIBUERT -> ForsendelseStatusTo.DISTRIBUERT
    ForsendelseStatus.DISTRIBUERT_LOKALT -> ForsendelseStatusTo.DISTRIBUERT_LOKALT
    ForsendelseStatus.SLETTET, ForsendelseStatus.AVBRUTT -> ForsendelseStatusTo.SLETTET
    ForsendelseStatus.FERDIGSTILT -> ForsendelseStatusTo.FERDIGSTILT
}

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
                    landkode = adresse.landkode
                )
            }
        )
    },
    gjelderIdent = this.gjelderIdent,
    tittel = this.dokumenter.hoveddokument?.tittel,
    tema = this.tema.name,
    saksnummer = this.saksnummer,
    forsendelseType = when (this.forsendelseType) {
        ForsendelseType.NOTAT -> ForsendelseTypeTo.NOTAT
        ForsendelseType.UTGÅENDE -> ForsendelseTypeTo.UTGÅENDE
    },
    status = this.tilForsendelseStatusTo(),
    opprettetDato = this.opprettetTidspunkt.toLocalDate(),
    dokumentDato = this.dokumentDato?.toLocalDate(),
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
            redigeringMetadata = it.metadata.hentRedigeringmetadata(),
            dokumentDato = it.dokumentDato,
            status = it.tilDokumentStatusTo()
        )
    }
)
