package no.nav.bidrag.dokument.forsendelse.model

import no.nav.bidrag.dokument.forsendelse.persistence.entity.Adresse
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Mottaker
import no.nav.bidrag.dokument.forsendelse.persistence.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.persistence.model.MottakerIdentType


fun MottakerDto.tilMottaker() = Mottaker(
    navn = this.navn,
    ident = this.ident,
    identType = when(this.identType){
        MottakerIdentTypeTo.ORGANISASJON -> MottakerIdentType.ORGANISASJON
        MottakerIdentTypeTo.FNR -> MottakerIdentType.FNR
        MottakerIdentTypeTo.SAMHANDLER -> MottakerIdentType.SAMHANDLER
        else -> null
    },
    adresse = this.adresse?.tilAdresse()
)
fun MottakerDto.tilIdentType(defaultVerdi: MottakerIdentType? = null) = when(this.identType){
    MottakerIdentTypeTo.ORGANISASJON -> MottakerIdentType.ORGANISASJON
    MottakerIdentTypeTo.FNR -> MottakerIdentType.FNR
    MottakerIdentTypeTo.SAMHANDLER -> MottakerIdentType.SAMHANDLER
    else -> defaultVerdi
}

fun MottakerAdresseTo.tilAdresse() =  Adresse(
    adresselinje1 = this.adresselinje1,
    adresselinje2 = this.adresselinje2,
    adresselinje3 = this.adresselinje3,
    bruksenhetsnummer = this.bruksenhetsnummer,
    landkode = this.landkode,
    postnummer = this.postnummer,
    poststed = this.poststed
)
fun DokumentForespørsel.tilArkivsystemDo() = when(this.arkivsystem){
    DokumentArkivSystemTo.BREVSERVER -> DokumentArkivSystem.BREVSERVER
    DokumentArkivSystemTo.JOARK -> DokumentArkivSystem.JOARK
}

fun DokumentForespørsel.tilDokumentStatusDo() = when(this.status){
    DokumentStatusTo.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
    DokumentStatusTo.UNDER_PRODUKSJON -> DokumentStatus.UNDER_PRODUKSJON
}

fun DokumentForespørsel.tilDokumentDo(forsendelse: Forsendelse, tilknyttetSom: DokumentTilknyttetSom? = null) = Dokument(
    forsendelse = forsendelse,
    tilknyttetSom = tilknyttetSom ?: when(this.tilknyttetSom) { DokumentTilknyttetSomTo.HOVEDDOKUMENT -> DokumentTilknyttetSom.HOVEDDOKUMENT else -> DokumentTilknyttetSom.VEDLEGG},
    tittel = this.tittel!!,
    arkivsystem = this.tilArkivsystemDo(),
    dokumentStatus = this.tilDokumentStatusDo(),
    eksternDokumentreferanse = this.dokumentreferanse,
    journalpostId = this.journalpostId,
    dokumentmalId = this.dokumentmalId
)