package no.nav.bidrag.dokument.forsendelse.utils

import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerIdentTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import java.time.LocalDate
import java.time.LocalDateTime

val DOKUMENT_FIL = "JVBERi0xLjcgQmFzZTY0IGVuY29kZXQgZnlzaXNrIGRva3VtZW50"

val DOKUMENTMAL_NOTAT = "BI090"
val DOKUMENTMAL_UTGÅENDE = "BI091"

val SAKSBEHANDLER_IDENT = "Z999444"
val SAKSBEHANDLER_NAVN = "Saksbehandlersen, Saksbehandler"
val SAKSNUMMER = "21312312"
val GJELDER_IDENT = "12312333123"
val MOTTAKER_IDENT = "2312333123"
val SAMHANDLER_ID = "80000365555"
val MOTTAKER_NAVN = "Nils Nilsen"
val JOURNALFØRENDE_ENHET = "4806"
val TITTEL_HOVEDDOKUMENT = "Tittel på hoveddokument"
val HOVEDDOKUMENT_DOKUMENTMAL = DOKUMENTMAL_UTGÅENDE
val TITTEL_VEDLEGG_1 = "Tittel på vedlegg 1"
val TITTEL_VEDLEGG_2 = "Tittel på vedlegg 2"

val ADRESSE_ADRESSELINJE1 = "Adresselinje1"
val ADRESSE_ADRESSELINJE2 = "Adresselinje2"
val ADRESSE_ADRESSELINJE3 = "Adresselinje3"
val ADRESSE_POSTNUMMER = "3040"
val ADRESSE_POSTSTED = "Drammen"
val ADRESSE_BRUKSENHETSNUMMER = "H0305"
val ADRESSE_LANDKODE3 = "NOR"
val ADRESSE_LANDKODE = "NO"
val SPRÅK_NORSK_BOKMÅL = "NB"

val NY_JOURNALPOSTID = "12312312312"

@DslMarker
annotation class OpprettForsendelseTestdataDsl

@OpprettForsendelseTestdataDsl
object med

@OpprettForsendelseTestdataDsl
object er
class ForsendelseBuilder {
    private var erNotat: Boolean = false
    private var journalførendeenhet: String = JOURNALFØRENDE_ENHET
    private var tittel: String? = null
    private var status: ForsendelseStatus = ForsendelseStatus.UNDER_PRODUKSJON
    private var arkivJournalpostId: String? = null
    private var tema: ForsendelseTema = ForsendelseTema.BID
    private var saksnummer: String = SAKSNUMMER
    private var gjelderIdent: String = GJELDER_IDENT
    private var mottaker: Mottaker? = Mottaker(ident = MOTTAKER_IDENT, navn = MOTTAKER_NAVN)
    private var opprettDokumenter: MutableList<Dokument> = mutableListOf()

    @OpprettForsendelseTestdataDsl
    infix fun med.arkivJournalpostId(_arkivJournalpostId: String) {
        arkivJournalpostId = _arkivJournalpostId
    }

    @OpprettForsendelseTestdataDsl
    infix fun med.status(_status: ForsendelseStatus) {
        status = _status
    }

    @OpprettForsendelseTestdataDsl
    infix fun med.journalførendeenhet(jfrEnhet: String) {
        journalførendeenhet = jfrEnhet
    }

    @OpprettForsendelseTestdataDsl
    infix fun er.notat(value: Boolean) {
        erNotat = value
        value.ifTrue { mottaker = null }
    }

    @OpprettForsendelseTestdataDsl
    infix fun med.saksnummer(_saksnummer: String) {
        saksnummer = _saksnummer
    }

    @OpprettForsendelseTestdataDsl
    infix fun med.tema(_tema: ForsendelseTema) {
        tema = _tema
    }

    @OpprettForsendelseTestdataDsl
    infix fun med.gjelderIdent(_gjelderIdent: String) {
        tittel = _gjelderIdent
    }

    @OpprettForsendelseTestdataDsl
    infix fun med.mottaker(_mottaker: Mottaker?) {
        mottaker = _mottaker
    }

    @OpprettForsendelseTestdataDsl
    operator fun Dokument.unaryPlus() {
        opprettDokumenter.add(this)
    }

    internal fun build(): Forsendelse {
        val forsendelse = Forsendelse(
            forsendelseType = if (erNotat) ForsendelseType.NOTAT else ForsendelseType.UTGÅENDE,
            enhet = journalførendeenhet,
            status = status,
            språk = "NB",
            saksnummer = saksnummer,
            gjelderIdent = gjelderIdent,
            mottaker = mottaker,
            tema = tema,
            opprettetAvIdent = SAKSBEHANDLER_IDENT,
            opprettetAvNavn = SAKSBEHANDLER_NAVN,
            endretAvIdent = SAKSBEHANDLER_IDENT,
            dokumenter = opprettDokumenter,
            journalpostIdFagarkiv = arkivJournalpostId
        )

        opprettDokumenter.forEach { it.forsendelse = forsendelse }
        return forsendelse
    }
}

@OpprettForsendelseTestdataDsl
fun opprettForsendelse(setup: ForsendelseBuilder.() -> Unit): Forsendelse {
    val forsendelseBuilder = ForsendelseBuilder()
    forsendelseBuilder.setup()
    return forsendelseBuilder.build()
}

fun opprettForsendelse2(
    erNotat: Boolean = false,
    journalførendeenhet: String = JOURNALFØRENDE_ENHET,
    distribusjonBestillingsId: String? = null,
    distribusjonsTidspunkt: LocalDateTime? = null,
    status: ForsendelseStatus = ForsendelseStatus.UNDER_PRODUKSJON,
    arkivJournalpostId: String? = null,
    kanal: DistribusjonKanal? = null,
    tema: ForsendelseTema = ForsendelseTema.BID,
    saksnummer: String = SAKSNUMMER,
    gjelderIdent: String = GJELDER_IDENT,
    mottaker: Mottaker? = Mottaker(ident = MOTTAKER_IDENT, navn = MOTTAKER_NAVN),
    dokumenter: List<Dokument> = listOf()
): Forsendelse {
    val forsendelse = Forsendelse(
        forsendelseType = if (erNotat) ForsendelseType.NOTAT else ForsendelseType.UTGÅENDE,
        enhet = journalførendeenhet,
        status = status,
        språk = "NB",
        saksnummer = saksnummer,
        gjelderIdent = gjelderIdent,
        mottaker = mottaker,
        tema = tema,
        opprettetAvIdent = SAKSBEHANDLER_IDENT,
        opprettetAvNavn = SAKSBEHANDLER_NAVN,
        endretAvIdent = SAKSBEHANDLER_IDENT,
        dokumenter = dokumenter,
        journalpostIdFagarkiv = arkivJournalpostId,
        distribusjonBestillingsId = distribusjonBestillingsId,
        distribuertTidspunkt = distribusjonsTidspunkt,
        distribusjonKanal = kanal
    )

    dokumenter.forEach { it.forsendelse = forsendelse }

    return forsendelse
}

fun nyttDokument(
    tittel: String = TITTEL_HOVEDDOKUMENT,
    journalpostId: String? = "123123",
    dokumentMalId: String? = HOVEDDOKUMENT_DOKUMENTMAL,
    dokumentreferanseOriginal: String? = "123213213",
    dokumentStatus: DokumentStatus = DokumentStatus.FERDIGSTILT,
    arkivsystem: DokumentArkivSystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
    rekkefølgeIndeks: Int = 0,
    slettet: Boolean = false,
    dokumentDato: LocalDateTime = LocalDateTime.now()
): Dokument {
    val forsendelse = opprettForsendelse2()
    return Dokument(
        arkivsystem = arkivsystem,
        tittel = tittel,
        journalpostIdOriginal = journalpostId,
        dokumentreferanseOriginal = dokumentreferanseOriginal,
        dokumentStatus = dokumentStatus,
        dokumentmalId = dokumentMalId,
        forsendelse = forsendelse,
        rekkefølgeIndeks = rekkefølgeIndeks,
        slettetTidspunkt = if (slettet) LocalDate.now() else null,
        dokumentDato = dokumentDato

    )
}

fun nyOpprettForsendelseForespørsel() = OpprettForsendelseForespørsel(
    gjelderIdent = GJELDER_IDENT,
    enhet = JOURNALFØRENDE_ENHET,
    saksnummer = SAKSNUMMER,
    mottaker = MottakerTo(
        ident = MOTTAKER_IDENT,
        navn = MOTTAKER_NAVN,
        språk = SPRÅK_NORSK_BOKMÅL,
        identType = MottakerIdentTypeTo.FNR,
        adresse = MottakerAdresseTo(
            adresselinje1 = ADRESSE_ADRESSELINJE1,
            adresselinje2 = ADRESSE_ADRESSELINJE2,
            adresselinje3 = ADRESSE_ADRESSELINJE3,
            poststed = ADRESSE_POSTSTED,
            postnummer = ADRESSE_POSTNUMMER,
            bruksenhetsnummer = ADRESSE_BRUKSENHETSNUMMER,
            landkode3 = ADRESSE_LANDKODE3,
            landkode = ADRESSE_LANDKODE
        )
    ),
    språk = SPRÅK_NORSK_BOKMÅL,
    dokumenter = listOf(
        OpprettDokumentForespørsel(
            tittel = TITTEL_HOVEDDOKUMENT,
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL
        ),
        OpprettDokumentForespørsel(
            tittel = TITTEL_VEDLEGG_1,
            dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
            journalpostId = "JOARK-123123213",
            dokumentreferanse = "123213"
        )
    )
)

fun nyOpprettJournalpostResponse(
    journalpostId: String = NY_JOURNALPOSTID,
    dokumenter: List<OpprettDokumentDto> =
        listOf(OpprettDokumentDto(tittel = "Tittel på dokument", dokumentreferanse = "dokref1"))
): OpprettJournalpostResponse {
    return OpprettJournalpostResponse(
        dokumenter = dokumenter,
        journalpostId = journalpostId
    )
}
