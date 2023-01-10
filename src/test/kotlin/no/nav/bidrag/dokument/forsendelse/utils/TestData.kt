package no.nav.bidrag.dokument.forsendelse.utils

import no.nav.bidrag.dokument.forsendelse.api.dto.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.database.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.model.ifTrue

val DOKUMENTMAL_NOTAT = "BI090"
val DOKUMENTMAL_UTGÅENDE = "BI091"

val SAKSBEHANDLER_IDENT = "Z999444"
val SAKSBEHANDLER_NAVN = "Saksbehandlersen, Saksbehandler"
val SAKSNUMMER = "21312312"
val GJELDER_IDENT = "12312333123"
val MOTTAKER_IDENT = "2312333123"
val MOTTAKER_NAVN = "Nils Nilsen"
val JOURNALFØRENDE_ENHET = "4806"
val TITTEL_HOVEDDOKUMENT = "Tittel på hoveddokument"
val HOVEDDOKUMENT_DOKUMENTMAL = DOKUMENTMAL_UTGÅENDE
val TITTEL_VEDLEGG_1 = "Tittel på vedlegg 1"
val TITTEL_VEDLEGG_2 = "Tittel på vedlegg 2"
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
    private var arkivJournalpostId: String? = null
    private var tilknyttSak: String = SAKSNUMMER
    private var gjelderIdent: String = GJELDER_IDENT
    private var mottaker: Mottaker? = Mottaker(ident = MOTTAKER_IDENT, navn = MOTTAKER_NAVN)
    private var opprettDokumenter: MutableList<Dokument> = mutableListOf()

    @OpprettForsendelseTestdataDsl
    infix fun med.arkivJournalpostId(_arkivJournalpostId: String) {
        arkivJournalpostId = _arkivJournalpostId
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
    infix fun med.tilknyttetSak(_tilknyttSak: String) {
        tilknyttSak = _tilknyttSak
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
            språk = "NB",
            saksnummer = tilknyttSak,
            gjelderIdent = gjelderIdent,
            mottaker = mottaker,
            opprettetAvIdent = SAKSBEHANDLER_IDENT,
            opprettetAvNavn = SAKSBEHANDLER_NAVN,
            endretAvIdent = SAKSBEHANDLER_IDENT,
            dokumenter = opprettDokumenter,
            arkivJournalpostId = arkivJournalpostId
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

fun nyDokument(
    tittel: String = TITTEL_HOVEDDOKUMENT,
    journalpostId: String? = "123123",
    dokumentMalId: String? = HOVEDDOKUMENT_DOKUMENTMAL,
    eksternDokumentreferanse: String? = "123213213",
    tilknyttetSom: DokumentTilknyttetSom = DokumentTilknyttetSom.HOVEDDOKUMENT,
    dokumentStatus: DokumentStatus = DokumentStatus.FERDIGSTILT,
    arkivsystem: DokumentArkivSystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
): Dokument {
    val forsendelse = opprettForsendelse {  }
    return Dokument(
        arkivsystem = arkivsystem,
        tittel = tittel,
        journalpostId = journalpostId,
        eksternDokumentreferanse = eksternDokumentreferanse,
        dokumentStatus = dokumentStatus,
        tilknyttetSom = tilknyttetSom,
        dokumentmalId = dokumentMalId,
        forsendelse = forsendelse

    )
}

fun nyOpprettForsendelseForespørsel() = OpprettForsendelseForespørsel(
    gjelderIdent = GJELDER_IDENT,
    enhet = JOURNALFØRENDE_ENHET,
    saksnummer = SAKSNUMMER,
    mottaker = MottakerTo(
        ident = MOTTAKER_IDENT,
        navn = MOTTAKER_NAVN,
        adresse = MottakerAdresseTo(
            adresselinje1 = "Adresselinje1",
            adresselinje2 = "Adresselinje2",
            adresselinje3 = "Adresselinje3",
            poststed = "Drammen",
            postnummer = "3040",
            bruksenhetsnummer = "H0305",
            landkode3 = "NOR",
            landkode = "NO"
        )
    ),
    forsendelseType = ForsendelseTypeTo.UTGÅENDE,
    språk = "NB",
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
