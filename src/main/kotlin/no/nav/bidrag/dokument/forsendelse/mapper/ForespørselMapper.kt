package no.nav.bidrag.dokument.forsendelse.mapper

import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.api.dto.*
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty

object ForespørselMapper {
    fun MottakerTo.tilMottakerDo() = Mottaker(
        navn = this.navn,
        ident = this.ident,
        språk = this.språk,
        identType = when(this.identType){
            MottakerIdentTypeTo.ORGANISASJON -> MottakerIdentType.ORGANISASJON
            MottakerIdentTypeTo.FNR -> MottakerIdentType.FNR
            MottakerIdentTypeTo.SAMHANDLER -> MottakerIdentType.SAMHANDLER
            else -> null
        },
        adresse = this.adresse?.tilAdresseDo()
    )
    fun MottakerTo.tilIdentType(defaultVerdi: MottakerIdentType? = null) = when(this.identType){
        MottakerIdentTypeTo.ORGANISASJON -> MottakerIdentType.ORGANISASJON
        MottakerIdentTypeTo.FNR -> MottakerIdentType.FNR
        MottakerIdentTypeTo.SAMHANDLER -> MottakerIdentType.SAMHANDLER
        else -> defaultVerdi
    }

    fun MottakerAdresseTo.tilAdresseDo() = Adresse(
        adresselinje1 = this.adresselinje1,
        adresselinje2 = this.adresselinje2,
        adresselinje3 = this.adresselinje3,
        bruksenhetsnummer = this.bruksenhetsnummer,
        landkode = this.landkode,
        landkode3 = this.landkode3,
        postnummer = this.postnummer,
        poststed = this.poststed
    )

    fun JournalpostId.tilArkivSystemDo() = when(this.arkivsystem){
        DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        DokumentArkivSystemDto.JOARK -> DokumentArkivSystem.JOARK
        else -> null
    }
    fun DokumentForespørsel.tilArkivsystemDo(): DokumentArkivSystem = when(this.arkivsystem){
        DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        DokumentArkivSystemDto.JOARK -> DokumentArkivSystem.JOARK
        else -> this.journalpostId?.tilArkivSystemDo() ?: DokumentArkivSystem.UKJENT
    }

    fun OpprettDokumentForespørsel.erBestillingAvNyttDokument() = this.bestillDokument && this.journalpostId.isNullOrEmpty() && this.dokumentreferanse.isNullOrEmpty() && this.dokumentmalId.isNotNullOrEmpty()
    fun OpprettDokumentForespørsel.tilDokumentStatusDo() = if (this.erBestillingAvNyttDokument())
        DokumentStatus.IKKE_BESTILT else if (!this.bestillDokument) DokumentStatus.UNDER_PRODUKSJON else when(this.status){
        DokumentStatusTo.BESTILLING_FEILET -> DokumentStatus.BESTILLING_FEILET
        DokumentStatusTo.IKKE_BESTILT -> DokumentStatus.IKKE_BESTILT
        DokumentStatusTo.AVBRUTT -> DokumentStatus.AVBRUTT
        DokumentStatusTo.UNDER_REDIGERING -> DokumentStatus.UNDER_REDIGERING
        DokumentStatusTo.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
        DokumentStatusTo.UNDER_PRODUKSJON -> DokumentStatus.UNDER_PRODUKSJON
    }

    fun OpprettDokumentForespørsel.tilDokumentDo(forsendelse: Forsendelse, indeks: Int) = Dokument(
        forsendelse = forsendelse,
        tilknyttetSom = if (indeks == 0) DokumentTilknyttetSom.HOVEDDOKUMENT else DokumentTilknyttetSom.VEDLEGG,
        tittel = this.tittel,
        arkivsystem = this.tilArkivsystemDo(),
        dokumentStatus = this.tilDokumentStatusDo(),
        eksternDokumentreferanse = this.dokumentreferanse,
        journalpostId = this.journalpostId?.utenPrefiks,
        dokumentmalId = this.dokumentmalId,
        metadata = this.metadata,
        rekkefølgeIndeks = indeks
    )
}