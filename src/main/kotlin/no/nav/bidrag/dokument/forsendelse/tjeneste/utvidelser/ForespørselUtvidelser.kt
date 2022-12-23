package no.nav.bidrag.dokument.forsendelse.tjeneste.utvidelser

import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentArkivSystemTo
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentTilknyttetSomTo
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalpostId
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerIdentTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.arkivsystem
import no.nav.bidrag.dokument.forsendelse.api.dto.harArkivPrefiks
import no.nav.bidrag.dokument.forsendelse.api.dto.utenPrefiks
import no.nav.bidrag.dokument.forsendelse.model.UgyldigForespørsel
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.model.validerErSann
import no.nav.bidrag.dokument.forsendelse.model.validerIkkeNullEllerTom
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.MottakerIdentType

fun MottakerTo.tilMottaker() = Mottaker(
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
fun MottakerTo.tilIdentType(defaultVerdi: MottakerIdentType? = null) = when(this.identType){
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

fun JournalpostId.tilArkivSystemDo() = when(this.arkivsystem){
    DokumentArkivSystemTo.BREVSERVER -> DokumentArkivSystem.BREVSERVER
    DokumentArkivSystemTo.JOARK -> DokumentArkivSystem.JOARK
    else -> null
}
fun DokumentForespørsel.tilArkivsystemDo(): DokumentArkivSystem = when(this.arkivsystem){
    DokumentArkivSystemTo.BREVSERVER -> DokumentArkivSystem.BREVSERVER
    DokumentArkivSystemTo.JOARK -> DokumentArkivSystem.JOARK
    else -> this.journalpostId?.tilArkivSystemDo() ?: DokumentArkivSystem.UKJENT
}

fun DokumentForespørsel.erBestillingAvNyDokument() = this.journalpostId.isNullOrEmpty() && this.dokumentreferanse.isNullOrEmpty() && this.dokumentmalId.isNotNullOrEmpty()
fun DokumentForespørsel.tilDokumentStatusDo() = if (this.erBestillingAvNyDokument())
    DokumentStatus.IKKE_BESTILT else when(this.status){
    DokumentStatusTo.IKKE_BESTILT -> DokumentStatus.IKKE_BESTILT
    DokumentStatusTo.BESTILT -> DokumentStatus.BESTILT
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
    journalpostId = this.journalpostId?.utenPrefiks,
    dokumentmalId = this.dokumentmalId,
    metadata = this.metadata
)

fun OppdaterForsendelseForespørsel.skalDokumentSlettes(dokumentreferanse: String?) = dokumenter.any { it.fjernTilknytning && it.dokumentreferanse == dokumentreferanse}
fun OppdaterForsendelseForespørsel.hent(dokumentreferanse: String?) = dokumenter.find { it.dokumentreferanse == dokumentreferanse }

fun OpprettDokumentForespørsel.valider(index: Number? = null, throwWhenInvalid: Boolean = true): List<String>{
    val feilmeldinger: MutableList<String> = mutableListOf()
    feilmeldinger.validerIkkeNullEllerTom(this.tittel, "Tittel på dokument ${index?:""} kan ikke være tom".replace("  ", ""))
    if (this.dokumentreferanse.isNotNullOrEmpty() || this.journalpostId.isNotNullOrEmpty()){
        feilmeldinger.validerErSann(this.journalpostId.isNotNullOrEmpty() || this.dokumentreferanse.isNotNullOrEmpty() && this.journalpostId.isNotNullOrEmpty(), "Både journalpostId og dokumentreferanse må settes hvis dokumentereferanse er satt dokumentreferanse=${this.dokumentreferanse}.")
        feilmeldinger.validerErSann(!this.journalpostId.isNullOrEmpty() && (this.journalpostId.harArkivPrefiks || this.arkivsystem != null), "JournalpostId må innholde arkiv prefiks eller arkivsystem må være satt")
    } else {
        feilmeldinger.validerIkkeNullEllerTom(this.dokumentmalId, "dokumentmalId må settes hvis dokumentreferanse eller journalpostId ikke er satt")
    }

    if (feilmeldinger.isNotEmpty() && throwWhenInvalid){
        throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
    }

    return feilmeldinger
}
fun OpprettForsendelseForespørsel.valider() {
    val feilmeldinger = mutableListOf<String>()

    this.dokumenter.forEachIndexed {i, it->
        feilmeldinger.addAll(it.valider(i, false))
    }

    if (feilmeldinger.isNotEmpty()){
        throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
    }
}

fun OppdaterForsendelseForespørsel.valider(eksisterendeForsendelse: Forsendelse) {

}