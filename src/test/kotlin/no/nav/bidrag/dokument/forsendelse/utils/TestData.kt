package no.nav.bidrag.dokument.forsendelse.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerIdentTypeTo
import no.nav.bidrag.dokument.forsendelse.api.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettDokumentForespørsel
import no.nav.bidrag.dokument.forsendelse.api.dto.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.dto.BehandlingDto
import no.nav.bidrag.dokument.forsendelse.model.ifTrue
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.DokumentMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.ForsendelseMetadataDo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DistribusjonKanal
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.sak.Bidragssakstatus
import no.nav.bidrag.domene.enums.sak.Sakskategori
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.vedtak.response.EngangsbeløpDto
import no.nav.bidrag.transport.behandling.vedtak.response.StønadsendringDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import no.nav.bidrag.transport.dokument.DokumentFormatDto
import no.nav.bidrag.transport.dokument.DokumentHendelse
import no.nav.bidrag.transport.dokument.DokumentHendelseType
import no.nav.bidrag.transport.dokument.DokumentMetadata
import no.nav.bidrag.transport.dokument.DokumentStatusDto
import no.nav.bidrag.transport.dokument.OpprettDokumentDto
import no.nav.bidrag.transport.dokument.OpprettJournalpostResponse
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto
import org.junit.Assert
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

val VALID_PDF_BASE64 =
    "JVBERi0xLjIgCjkgMCBvYmoKPDwKPj4Kc3RyZWFtCkJULyAzMiBUZiggIFlPVVIgVEVYVCBIRVJFICAgKScgRVQKZW5kc3RyZWFtCmVuZG9iago0IDAgb2JqCjw8Ci9UeXBlIC9QYWdlCi9QYXJlbnQgNSAwIFIKL0NvbnRlbnRzIDkgMCBSCj4+CmVuZG9iago1IDAgb2JqCjw8Ci9LaWRzIFs0IDAgUiBdCi9Db3VudCAxCi9UeXBlIC9QYWdlcwovTWVkaWFCb3ggWyAwIDAgMjUwIDUwIF0KPj4KZW5kb2JqCjMgMCBvYmoKPDwKL1BhZ2VzIDUgMCBSCi9UeXBlIC9DYXRhbG9nCj4+CmVuZG9iagp0cmFpbGVyCjw8Ci9Sb290IDMgMCBSCj4+CiUlRU9G"

val DOKUMENT_FIL = "JVBERi0xLjcgQmFzZTY0IGVuY29kZXQgZnlzaXNrIGRva3VtZW50"

val DOKUMENTMAL_NOTAT = "BI090"
val DOKUMENTMAL_UTGÅENDE = "BI091"
val DOKUMENTMAL_UTGÅENDE_2 = "MAL1"
val DOKUMENTMAL_STATISK_VEDLEGG = "STATISK_VEDLEGG_MAL"
val DOKUMENTMAL_STATISK_VEDLEGG_REDIGERBAR = "STATISK_VEDLEGG_MAL_REDIGERBAR"
val DOKUMENTMAL_UTGÅENDE_3 = "MAL2"
val DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES = "MAL4_KAN_IKKE_BESTILLES"
val DOKUMENTMAL_UTGÅENDE_KAN_IKKE_BESTILLES_2 = "MAL5_KAN_IKKE_BESTILLES"

val SAKSBEHANDLER_IDENT = "Z999444"
val SAKSBEHANDLER_NAVN = "Saksbehandlersen, Saksbehandler"
val SAKSNUMMER = "21312312"
val GJELDER_IDENT = "12312333123"
val GJELDER_IDENT_BM = "34344343434"
val GJELDER_IDENT_BP = "545454545"
val GJELDER_IDENT_BA = "123213213213"
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

fun jsonToString(data: Any): String =
    try {
        ObjectMapper().findAndRegisterModules().writeValueAsString(data)
    } catch (e: JsonProcessingException) {
        Assert.fail(e.message)
        ""
    }

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
        val forsendelse =
            Forsendelse(
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
                journalpostIdFagarkiv = arkivJournalpostId,
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
    tittel: String? = null,
    mottaker: Mottaker? = Mottaker(ident = MOTTAKER_IDENT, navn = MOTTAKER_NAVN),
    dokumenter: List<Dokument> = listOf(),
    behandlingInfo: BehandlingInfo? = null,
    endretAvIdent: String = SAKSBEHANDLER_IDENT,
    metadata: ForsendelseMetadataDo? = null,
    medId: Boolean = false,
): Forsendelse {
    val forsendelse =
        Forsendelse(
            forsendelseId = if (medId) 1 else null,
            forsendelseType = if (erNotat) ForsendelseType.NOTAT else ForsendelseType.UTGÅENDE,
            enhet = journalførendeenhet,
            status = status,
            behandlingInfo = behandlingInfo,
            tittel = tittel,
            språk = "NB",
            saksnummer = saksnummer,
            gjelderIdent = gjelderIdent,
            mottaker = mottaker,
            tema = tema,
            opprettetAvIdent = SAKSBEHANDLER_IDENT,
            opprettetAvNavn = SAKSBEHANDLER_NAVN,
            endretAvIdent = endretAvIdent,
            dokumenter = dokumenter,
            journalpostIdFagarkiv = arkivJournalpostId,
            distribusjonBestillingsId = distribusjonBestillingsId,
            distribuertTidspunkt = distribusjonsTidspunkt,
            distribusjonKanal = kanal,
            metadata = metadata,
        )

    dokumenter.forEach { it.forsendelse = forsendelse }

    return forsendelse
}

fun opprettAdresseDo() =
    Adresse(
        adresselinje1 = ADRESSE_ADRESSELINJE1,
        adresselinje2 = ADRESSE_ADRESSELINJE2,
        adresselinje3 = ADRESSE_ADRESSELINJE3,
        landkode = ADRESSE_LANDKODE,
        landkode3 = ADRESSE_LANDKODE3,
        bruksenhetsnummer = ADRESSE_BRUKSENHETSNUMMER,
        postnummer = ADRESSE_POSTNUMMER,
        poststed = ADRESSE_POSTSTED,
    )

fun nyttDokument(
    tittel: String = TITTEL_HOVEDDOKUMENT,
    journalpostId: String? = "123123",
    dokumentMalId: String? = HOVEDDOKUMENT_DOKUMENTMAL,
    dokumentreferanseOriginal: String? = "123213213",
    dokumentStatus: DokumentStatus = DokumentStatus.FERDIGSTILT,
    arkivsystem: DokumentArkivSystem = DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER,
    rekkefølgeIndeks: Int = 0,
    slettet: Boolean = false,
    dokumentDato: LocalDateTime = LocalDateTime.now(),
    opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    metadata: DokumentMetadataDo = DokumentMetadataDo(),
): Dokument {
    val forsendelse = opprettForsendelse2()
    return Dokument(
        metadata = metadata,
        arkivsystem = arkivsystem,
        tittel = tittel,
        journalpostIdOriginal = journalpostId,
        dokumentreferanseOriginal = dokumentreferanseOriginal,
        dokumentStatus = dokumentStatus,
        dokumentmalId = dokumentMalId,
        forsendelse = forsendelse,
        rekkefølgeIndeks = rekkefølgeIndeks,
        slettetTidspunkt = if (slettet) LocalDate.now() else null,
        dokumentDato = dokumentDato,
        opprettetTidspunkt = opprettetTidspunkt,
    )
}

fun opprettHendelse(
    dokumentreferanse: String,
    status: DokumentStatusDto = DokumentStatusDto.UNDER_REDIGERING,
): DokumentHendelse =
    DokumentHendelse(
        dokumentreferanse = dokumentreferanse,
        arkivSystem = DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER,
        hendelseType = DokumentHendelseType.ENDRING,
        sporingId = "sporing",
        status = status,
    )

fun nyOpprettForsendelseForespørsel() =
    OpprettForsendelseForespørsel(
        gjelderIdent = GJELDER_IDENT,
        enhet = JOURNALFØRENDE_ENHET,
        saksnummer = SAKSNUMMER,
        mottaker =
            MottakerTo(
                ident = MOTTAKER_IDENT,
                navn = MOTTAKER_NAVN,
                språk = SPRÅK_NORSK_BOKMÅL,
                identType = MottakerIdentTypeTo.FNR,
                adresse =
                    MottakerAdresseTo(
                        adresselinje1 = ADRESSE_ADRESSELINJE1,
                        adresselinje2 = ADRESSE_ADRESSELINJE2,
                        adresselinje3 = ADRESSE_ADRESSELINJE3,
                        poststed = ADRESSE_POSTSTED,
                        postnummer = ADRESSE_POSTNUMMER,
                        bruksenhetsnummer = ADRESSE_BRUKSENHETSNUMMER,
                        landkode3 = ADRESSE_LANDKODE3,
                        landkode = ADRESSE_LANDKODE,
                    ),
            ),
        språk = SPRÅK_NORSK_BOKMÅL,
        dokumenter =
            listOf(
                OpprettDokumentForespørsel(
                    tittel = TITTEL_HOVEDDOKUMENT,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                ),
                OpprettDokumentForespørsel(
                    tittel = TITTEL_VEDLEGG_1,
                    dokumentmalId = HOVEDDOKUMENT_DOKUMENTMAL,
                    journalpostId = "JOARK-123123213",
                    dokumentreferanse = "123213",
                ),
            ),
    )

fun nyOpprettJournalpostResponse(
    journalpostId: String = NY_JOURNALPOSTID,
    dokumenter: List<OpprettDokumentDto> =
        listOf(OpprettDokumentDto(tittel = "Tittel på dokument", dokumentreferanse = "dokref1")),
): OpprettJournalpostResponse =
    OpprettJournalpostResponse(
        dokumenter = dokumenter,
        journalpostId = journalpostId,
    )

fun opprettDokumentMetadata(
    journalpostId: String,
    dokumentreferanse: String? = null,
): DokumentMetadata =
    DokumentMetadata(
        journalpostId = journalpostId,
        dokumentreferanse = dokumentreferanse,
        tittel = "Tittel på dokument",
        status = DokumentStatusDto.UNDER_REDIGERING,
        arkivsystem = DokumentArkivSystemDto.JOARK,
        format = DokumentFormatDto.PDF,
    )

fun opprettDokumentMetadataListe(journalpostId: String): List<DokumentMetadata> =
    listOf(
        opprettDokumentMetadata(journalpostId),
    )

fun opprettBehandlingDto(): BehandlingDto =
    BehandlingDto(
        stønadstype = Stønadstype.FORSKUDD,
        søktAv = SøktAvType.BIDRAGSMOTTAKER,
        vedtakstype = Vedtakstype.ENDRING,
        saksnummer = SAKSNUMMER,
        behandlerenhet = JOURNALFØRENDE_ENHET,
        id = 1,
        søknadsid = 1,
    )

fun opprettVedtakDto(): VedtakDto =
    VedtakDto(
        kilde = Vedtakskilde.AUTOMATISK,
        type = Vedtakstype.FASTSETTELSE,
        stønadsendringListe = listOf(opprettStonadsEndringDto()),
        engangsbeløpListe = emptyList(),
        opprettetAv = "",
        opprettetTidspunkt = LocalDateTime.now(),
        vedtakstidspunkt = LocalDateTime.now(),
        enhetsnummer = Enhetsnummer(JOURNALFØRENDE_ENHET),
        behandlingsreferanseListe = emptyList(),
        grunnlagListe =
            listOf(
                GrunnlagDto(
                    innhold = ObjectMapper().createObjectNode(),
                    type = Grunnlagstype.DELBEREGNING_SUM_INNTEKT,
                    referanse = "",
                ),
            ),
        opprettetAvNavn = "",
        fastsattILand = "",
        innkrevingUtsattTilDato = LocalDate.now(),
        kildeapplikasjon = "",
    )

fun opprettEngangsbelopDto(
    type: Engangsbeløptype = Engangsbeløptype.SÆRBIDRAG,
    resultatkode: String = "",
) = EngangsbeløpDto(
    type,
    sak = Saksnummer(SAKSNUMMER),
    skyldner = Personident(""),
    kravhaver = Personident(""),
    mottaker = Personident(""),
    innkreving = Innkrevingstype.MED_INNKREVING,
    beslutning = Beslutningstype.ENDRING,
    omgjørVedtakId = 1,
    eksternReferanse = "",
    beløp = BigDecimal.ONE,
    delytelseId = "",
    grunnlagReferanseListe = emptyList(),
    referanse = "",
    resultatkode = resultatkode,
    valutakode = "",
)

fun opprettStonadsEndringDto() =
    StønadsendringDto(
        Stønadstype.BIDRAG,
        sak = Saksnummer(SAKSNUMMER),
        skyldner = Personident(""),
        kravhaver = Personident(""),
        mottaker = Personident(""),
        innkreving = Innkrevingstype.MED_INNKREVING,
        beslutning = Beslutningstype.ENDRING,
        periodeListe = emptyList(),
        omgjørVedtakId = 1,
        eksternReferanse = "",
        førsteIndeksreguleringsår = 2044,
        grunnlagReferanseListe = emptyList(),
    )

fun opprettSak(): BidragssakDto =
    BidragssakDto(
        eierfogd = Enhetsnummer(JOURNALFØRENDE_ENHET),
        kategori = Sakskategori.U,
        levdeAdskilt = false,
        opprettetDato = LocalDate.now(),
        saksnummer = Saksnummer(SAKSNUMMER),
        saksstatus = Bidragssakstatus.IN,
        ukjentPart = false,
        roller =
            listOf(
                RolleDto(Personident(GJELDER_IDENT_BM), Rolletype.BIDRAGSMOTTAKER),
                RolleDto(Personident(GJELDER_IDENT_BP), Rolletype.BIDRAGSPLIKTIG),
                RolleDto(Personident(GJELDER_IDENT_BA), Rolletype.BARN),
            ),
    )
