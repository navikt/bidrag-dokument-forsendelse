package no.nav.bidrag.dokument.forsendelse.mapper

import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalpostId
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerIdentTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.arkivsystem
import no.nav.bidrag.dokument.forsendelse.api.dto.erForsendelse
import no.nav.bidrag.dokument.forsendelse.api.dto.utenPrefiks
import no.nav.bidrag.dokument.forsendelse.model.PersonIdent
import no.nav.bidrag.dokument.forsendelse.model.alpha3LandkodeTilAlpha2
import no.nav.bidrag.dokument.forsendelse.model.erSamhandler
import no.nav.bidrag.dokument.forsendelse.model.fjernKontrollTegn
import no.nav.bidrag.dokument.forsendelse.model.isNotNullOrEmpty
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.service.hentNorskPoststed
import no.nav.bidrag.transport.person.PersonDto
import java.time.LocalDateTime

object ForespørselMapper {
    fun JournalTema.toForsendelseTema() = when (this) {
        JournalTema.FAR -> ForsendelseTema.FAR
        else -> ForsendelseTema.BID
    }

    fun MottakerTo.tilMottakerDo(person: PersonDto?, språk: String) = Mottaker(
        navn = this.navn ?: person?.visningsnavn,
        ident = this.ident,
        språk = språk.uppercase(),
        identType = when (this.identType) {
            MottakerIdentTypeTo.FNR -> MottakerIdentType.FNR
            MottakerIdentTypeTo.SAMHANDLER -> MottakerIdentType.SAMHANDLER
            else -> this.ident?.tilIdentType()
        },
        adresse = this.adresse?.tilAdresseDo()
    )

    fun PersonIdent.tilIdentType() =
        if (this.erSamhandler()) {
            MottakerIdentType.SAMHANDLER
        } else {
            MottakerIdentType.FNR
        }

    fun MottakerAdresseTo.tilAdresseDo() = Adresse(
        adresselinje1 = this.adresselinje1,
        adresselinje2 = this.adresselinje2,
        adresselinje3 = this.adresselinje3,
        bruksenhetsnummer = this.bruksenhetsnummer,
        landkode = this.landkode ?: alpha3LandkodeTilAlpha2(this.landkode3),
        landkode3 = this.landkode3,
        postnummer = this.postnummer,
        poststed = this.poststed ?: hentNorskPoststed(this.postnummer, this.landkode ?: this.landkode3)
    )

    fun JournalpostId.tilArkivSystemDo() = this.arkivsystem?.let { DokumentArkivSystem.valueOf(it.name) }

    fun OpprettDokumentForespørsel.tilArkivsystemDo(): DokumentArkivSystem =
        if (this.journalpostId?.erForsendelse == true) {
            DokumentArkivSystem.FORSENDELSE
        } else {
            this.arkivsystem?.let { DokumentArkivSystem.valueOf(it.name) } ?: this.journalpostId?.tilArkivSystemDo() ?: DokumentArkivSystem.UKJENT
        }

    fun OpprettDokumentForespørsel.erBestillingAvNyttDokument() =
        this.journalpostId.isNullOrEmpty() && this.dokumentreferanse.isNullOrEmpty() && this.dokumentmalId.isNotNullOrEmpty()

    fun OpprettDokumentForespørsel.erFraAnnenKilde() = !(dokumentreferanse == null && journalpostId == null)
    fun OpprettDokumentForespørsel.tilDokumentStatusDo() = if (bestillDokument && this.erBestillingAvNyttDokument()) {
        DokumentStatus.IKKE_BESTILT
    } else if (this.erBestillingAvNyttDokument()) {
        DokumentStatus.UNDER_PRODUKSJON
    } else {
        DokumentStatus.MÅ_KONTROLLERES
    }

    fun OpprettDokumentForespørsel.tilDokumentDo(forsendelse: Forsendelse, indeks: Int) = Dokument(
        forsendelse = forsendelse,
        tittel = this.tittel.fjernKontrollTegn(),
        språk = this.språk ?: forsendelse.språk,
        arkivsystem = this.tilArkivsystemDo(),
        dokumentStatus = this.tilDokumentStatusDo(),
        dokumentreferanseOriginal = this.dokumentreferanse,
        dokumentDato = this.dokumentDato ?: LocalDateTime.now(),
        journalpostIdOriginal = this.journalpostId?.utenPrefiks,
        dokumentmalId = this.dokumentmalId,
        rekkefølgeIndeks = indeks
    )

    fun OppdaterDokumentForespørsel.tilOpprettDokumentForespørsel() =
        OpprettDokumentForespørsel(
            tittel = tittel ?: "",
            dokumentreferanse = dokumentreferanse,
            dokumentmalId = dokumentmalId,
            språk = språk,
            journalpostId = journalpostId,
            dokumentDato = dokumentDato,
            arkivsystem = this.arkivsystem
        )
}
