package no.nav.bidrag.dokument.forsendelse.mapper

import no.nav.bidrag.dokument.dto.DokumentArkivSystemDto
import no.nav.bidrag.dokument.forsendelse.api.dto.DokumentStatusTo
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalTema
import no.nav.bidrag.dokument.forsendelse.api.dto.JournalpostId
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerIdentTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OppdaterDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.arkivsystem
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
import no.nav.bidrag.dokument.forsendelse.service.KodeverkService
import no.nav.bidrag.transport.person.PersonDto
import java.time.LocalDateTime

object ForespørselMapper {
    fun JournalTema.toForsendelseTema() = when (this) {
        JournalTema.FAR -> ForsendelseTema.FAR
        else -> ForsendelseTema.BID
    }

    fun MottakerTo.tilMottakerDo(person: PersonDto?, språk: String) = Mottaker(
        navn = this.navn ?: person?.navn?.verdi,
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
        poststed = this.poststed ?: KodeverkService.hentNorskPoststed(this.postnummer, this.landkode ?: this.landkode3)
    )

    fun JournalpostId.tilArkivSystemDo() = when (this.arkivsystem) {
        DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        DokumentArkivSystemDto.JOARK -> DokumentArkivSystem.JOARK
        else -> null
    }

    fun OpprettDokumentForespørsel.tilArkivsystemDo(): DokumentArkivSystem = when (this.arkivsystem) {
        DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
        DokumentArkivSystemDto.JOARK -> DokumentArkivSystem.JOARK
        else -> this.journalpostId?.tilArkivSystemDo() ?: DokumentArkivSystem.UKJENT
    }

    fun OpprettDokumentForespørsel.erBestillingAvNyttDokument() =
        this.journalpostId.isNullOrEmpty() && this.dokumentreferanse.isNullOrEmpty() && this.dokumentmalId.isNotNullOrEmpty()

    fun OpprettDokumentForespørsel.tilDokumentStatusDo() = if (bestillDokument && this.erBestillingAvNyttDokument()) {
        DokumentStatus.IKKE_BESTILT
    } else if (this.erBestillingAvNyttDokument()) {
        DokumentStatus.UNDER_PRODUKSJON
    } else {
        when (this.status) {
            DokumentStatusTo.BESTILLING_FEILET -> DokumentStatus.BESTILLING_FEILET
            DokumentStatusTo.IKKE_BESTILT -> DokumentStatus.IKKE_BESTILT
            DokumentStatusTo.AVBRUTT -> DokumentStatus.AVBRUTT
            DokumentStatusTo.UNDER_REDIGERING -> DokumentStatus.UNDER_REDIGERING
            DokumentStatusTo.FERDIGSTILT -> DokumentStatus.FERDIGSTILT
            DokumentStatusTo.UNDER_PRODUKSJON -> DokumentStatus.UNDER_PRODUKSJON
            DokumentStatusTo.MÅ_KONTROLLERES -> DokumentStatus.MÅ_KONTROLLERES
            DokumentStatusTo.KONTROLLERT -> DokumentStatus.KONTROLLERT
        }
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

    fun OppdaterDokumentForespørsel.tilOpprettDokumentForespørsel() = OpprettDokumentForespørsel(
        tittel = tittel!!,
        dokumentreferanse = dokumentreferanse,
        status = DokumentStatusTo.MÅ_KONTROLLERES,
        dokumentmalId = dokumentmalId,
        journalpostId = journalpostId,
        dokumentDato = dokumentDato,
        arkivsystem = arkivsystem
    )
}
