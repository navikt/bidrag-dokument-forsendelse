package no.nav.bidrag.dokument.forsendelse.mapper

import no.nav.bidrag.dokument.forsendelse.model.alpha3LandkodeTilAlpha2
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Adresse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Ettersendingsoppgave
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Mottaker
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseTema
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.MottakerIdentType
import no.nav.bidrag.dokument.forsendelse.service.FORSENDELSE_APP_ID
import no.nav.bidrag.dokument.forsendelse.service.hentSamhandler
import no.nav.bidrag.dokument.forsendelse.utvidelser.dokumentDato
import no.nav.bidrag.dokument.forsendelse.utvidelser.erAlleFerdigstilt
import no.nav.bidrag.dokument.forsendelse.utvidelser.erNotat
import no.nav.bidrag.dokument.forsendelse.utvidelser.erUtgående
import no.nav.bidrag.dokument.forsendelse.utvidelser.forsendelseIdMedPrefix
import no.nav.bidrag.dokument.forsendelse.utvidelser.hoveddokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.ikkeSlettetSortertEtterRekkefølge
import no.nav.bidrag.domene.ident.SamhandlerId
import no.nav.bidrag.transport.dokument.AktorDto
import no.nav.bidrag.transport.dokument.AvsenderMottakerDto
import no.nav.bidrag.transport.dokument.AvsenderMottakerDtoIdType
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import no.nav.bidrag.transport.dokument.DokumentDto
import no.nav.bidrag.transport.dokument.DokumentStatusDto
import no.nav.bidrag.transport.dokument.DokumentType
import no.nav.bidrag.transport.dokument.Fagomrade
import no.nav.bidrag.transport.dokument.JournalpostDto
import no.nav.bidrag.transport.dokument.JournalpostStatus
import no.nav.bidrag.transport.dokument.KodeDto
import no.nav.bidrag.transport.dokument.MottakerAdresseTo
import no.nav.bidrag.transport.dokument.forsendelse.BehandlingInfoResponseDto
import no.nav.bidrag.transport.dokument.forsendelse.DokumentRespons
import no.nav.bidrag.transport.dokument.forsendelse.DokumentStatusTo
import no.nav.bidrag.transport.dokument.forsendelse.EttersendingsoppgaveDto
import no.nav.bidrag.transport.dokument.forsendelse.EttersendingsoppgaveVedleggDto
import no.nav.bidrag.transport.dokument.forsendelse.ForsendelseResponsTo
import no.nav.bidrag.transport.dokument.forsendelse.ForsendelseStatusTo
import no.nav.bidrag.transport.dokument.forsendelse.ForsendelseTypeTo
import no.nav.bidrag.transport.dokument.forsendelse.MottakerTo
import no.nav.bidrag.transport.dokument.forsendelse.OpprettDokumentForespørsel

fun Dokument.tilDokumentStatusDto() =
    when (dokumentStatus) {
        DokumentStatus.MÅ_KONTROLLERES -> DokumentStatusDto.UNDER_REDIGERING
        DokumentStatus.KONTROLLERT -> DokumentStatusDto.FERDIGSTILT
        DokumentStatus.BESTILLING_FEILET -> DokumentStatusDto.BESTILLING_FEILET
        DokumentStatus.UNDER_REDIGERING -> DokumentStatusDto.UNDER_REDIGERING
        DokumentStatus.UNDER_PRODUKSJON -> DokumentStatusDto.UNDER_PRODUKSJON
        DokumentStatus.FERDIGSTILT -> DokumentStatusDto.FERDIGSTILT
        DokumentStatus.IKKE_BESTILT -> DokumentStatusDto.IKKE_BESTILT
        DokumentStatus.AVBRUTT -> DokumentStatusDto.AVBRUTT
    }

fun Dokument.tilDokumentStatusTo() =
    when (dokumentStatus) {
        DokumentStatus.MÅ_KONTROLLERES -> DokumentStatusTo.MÅ_KONTROLLERES
        DokumentStatus.KONTROLLERT -> DokumentStatusTo.KONTROLLERT
        DokumentStatus.UNDER_REDIGERING -> DokumentStatusTo.UNDER_REDIGERING
        DokumentStatus.UNDER_PRODUKSJON -> DokumentStatusTo.UNDER_PRODUKSJON
        DokumentStatus.FERDIGSTILT -> DokumentStatusTo.FERDIGSTILT
        DokumentStatus.IKKE_BESTILT -> DokumentStatusTo.IKKE_BESTILT
        DokumentStatus.BESTILLING_FEILET -> DokumentStatusTo.BESTILLING_FEILET
        DokumentStatus.AVBRUTT -> DokumentStatusTo.AVBRUTT
    }

fun Dokument.tilArkivSystemDto() =
    when (arkivsystem) {
        DokumentArkivSystem.BIDRAG -> DokumentArkivSystemDto.BIDRAG
        DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER
        DokumentArkivSystem.JOARK -> DokumentArkivSystemDto.JOARK
        DokumentArkivSystem.FORSENDELSE -> DokumentArkivSystemDto.FORSENDELSE
        else -> DokumentArkivSystemDto.UKJENT
    }

fun Dokument.tilOpprettDokumentForespørsel() =
    OpprettDokumentForespørsel(
        tittel = tittel,
        dokumentreferanse = dokumentreferanse,
        dokumentmalId = dokumentmalId,
        journalpostId = forsendelseId.toString(),
        dokumentDato = dokumentDato,
        arkivsystem = this.tilArkivSystemDto(),
    )

private fun Forsendelse.tilJournalpostStatus() =
    when (this.status) {
        ForsendelseStatus.DISTRIBUERT_LOKALT, ForsendelseStatus.DISTRIBUERT -> JournalpostStatus.EKSPEDERT
        ForsendelseStatus.SLETTET -> JournalpostStatus.UTGÅR
        ForsendelseStatus.AVBRUTT -> JournalpostStatus.FEILREGISTRERT
        ForsendelseStatus.FERDIGSTILT -> if (erUtgående) JournalpostStatus.KLAR_FOR_DISTRIBUSJON else JournalpostStatus.FERDIGSTILT
        ForsendelseStatus.UNDER_PRODUKSJON ->
            if (this.dokumenter.erAlleFerdigstilt) {
                if (erUtgående) JournalpostStatus.KLAR_FOR_DISTRIBUSJON else JournalpostStatus.FERDIGSTILT
            } else {
                JournalpostStatus.UNDER_PRODUKSJON
            }

        ForsendelseStatus.UNDER_OPPRETTELSE -> JournalpostStatus.UNDER_OPPRETTELSE
    }

fun Forsendelse.tilJournalpostDto(dokumenterMetadata: Map<String, DokumentDtoMetadata>? = emptyMap()) =
    JournalpostDto(
        avsenderMottaker =
            this.mottaker?.let {
                AvsenderMottakerDto(
                    navn = it.navn,
                    ident = it.ident,
                    type =
                        when (it.identType) {
                            MottakerIdentType.SAMHANDLER -> AvsenderMottakerDtoIdType.SAMHANDLER
                            else -> AvsenderMottakerDtoIdType.FNR
                        },
                    adresse =
                        it.hentAdresse()?.let { adresse ->
                            MottakerAdresseTo(
                                adresselinje1 = adresse.adresselinje1,
                                adresselinje2 = adresse.adresselinje2,
                                adresselinje3 = adresse.adresselinje3,
                                bruksenhetsnummer = adresse.bruksenhetsnummer,
                                poststed = adresse.poststed,
                                postnummer = adresse.postnummer,
                                landkode = adresse.landkode ?: alpha3LandkodeTilAlpha2(adresse.landkode3),
                                landkode3 = adresse.landkode3,
                            )
                        },
                )
            },
        joarkJournalpostId = this.journalpostIdFagarkiv,
        språk = this.språk,
        gjelderIdent = this.gjelderIdent,
        gjelderAktor = AktorDto(this.gjelderIdent),
        brevkode = KodeDto(this.dokumenter.hoveddokument?.dokumentmalId),
        innhold =
            kotlin.run {
                val tittel = if (this.status == ForsendelseStatus.UNDER_OPPRETTELSE) tittel else this.dokumenter.hoveddokument?.tittel
                if (metadata?.skalDistribueresAutomatisk() == true) {
                    "(Distribueres automatisk) $tittel"
                } else {
                    tittel
                }
            },
        fagomrade =
            when (tema) {
                ForsendelseTema.FAR -> Fagomrade.FARSKAP
                else -> Fagomrade.BIDRAG
            },
        dokumentType =
            when (this.forsendelseType) {
                ForsendelseType.NOTAT -> DokumentType.NOTAT
                ForsendelseType.UTGÅENDE -> DokumentType.UTGÅENDE
            },
        journalfortAv = if (opprettetAvIdent == "bisys") "Bisys (Automatisk jobb)" else opprettetAvIdent,
        journalstatus = tilJournalpostStatus().kode,
        status = tilJournalpostStatus(),
        journalpostId = forsendelseIdMedPrefix,
        dokumentDato =
            if (erNotat) {
                this.dokumentDato?.toLocalDate()
                    ?: this.opprettetTidspunkt.toLocalDate()
            } else {
                this.opprettetTidspunkt.toLocalDate()
            },
        journalfortDato = this.opprettetTidspunkt.toLocalDate(),
        journalforendeEnhet = this.enhet,
        feilfort = status == ForsendelseStatus.AVBRUTT,
        dokumenter =
            this.dokumenter.ikkeSlettetSortertEtterRekkefølge.map { dokument ->
                DokumentDto(
                    dokumentreferanse = dokument.dokumentreferanse,
                    journalpostId = dokument.forsendelseIdMedPrefix,
                    arkivSystem = dokument.tilArkivSystemDto(),
                    metadata = dokumenterMetadata?.get(dokument.dokumentreferanse)?.toMap() ?: emptyMap(),
                    tittel = dokument.tittel,
                    dokumentmalId = dokument.dokumentmalId,
                    status = dokument.tilDokumentStatusDto(),
                )
            },
        sakstilknytninger = listOf(saksnummer),
        opprettetAvIdent = this.opprettetAvIdent,
    )

@Suppress("ktlint:standard:property-naming")
class DokumentDtoMetadata : MutableMap<String, String> by hashMapOf() {
    companion object {
        fun from(initValue: Map<String, String> = hashMapOf()): DokumentDtoMetadata {
            val dokmap = DokumentDtoMetadata()
            dokmap.putAll(initValue)
            return dokmap
        }
    }

    private val ORIGINAL_JOURNALPOST_ID = "originalJournalpostId"
    private val ORIGINAL_DOKUMENTREFERANSE = "originalDokumentreferanse"

    fun oppdaterOriginalJournalpostId(originalJournalpostid: String?) {
        remove(ORIGINAL_JOURNALPOST_ID)
        originalJournalpostid?.let { put(ORIGINAL_JOURNALPOST_ID, originalJournalpostid) }
    }

    fun hentOriginalJournalpostId(): String? = get(ORIGINAL_JOURNALPOST_ID)

    fun oppdaterOriginalDokumentreferanse(originalDokumentreferanse: String?) {
        remove(ORIGINAL_DOKUMENTREFERANSE)
        originalDokumentreferanse?.let { put(ORIGINAL_DOKUMENTREFERANSE, originalDokumentreferanse) }
    }

    fun hentOriginalDokumentreferanse(): String? = get(ORIGINAL_DOKUMENTREFERANSE)

    fun copy(): DokumentDtoMetadata = from(this)
}

fun Forsendelse.tilForsendelseStatusTo() =
    when (this.status) {
        ForsendelseStatus.UNDER_PRODUKSJON -> ForsendelseStatusTo.UNDER_PRODUKSJON
        ForsendelseStatus.DISTRIBUERT -> ForsendelseStatusTo.DISTRIBUERT
        ForsendelseStatus.DISTRIBUERT_LOKALT -> ForsendelseStatusTo.DISTRIBUERT_LOKALT
        ForsendelseStatus.SLETTET, ForsendelseStatus.AVBRUTT -> ForsendelseStatusTo.SLETTET
        ForsendelseStatus.FERDIGSTILT -> ForsendelseStatusTo.FERDIGSTILT
        ForsendelseStatus.UNDER_OPPRETTELSE -> ForsendelseStatusTo.UNDER_OPPRETTELSE
    }

fun Forsendelse.tilForsendelseType() =
    when (this.forsendelseType) {
        ForsendelseType.NOTAT -> ForsendelseTypeTo.NOTAT
        ForsendelseType.UTGÅENDE -> ForsendelseTypeTo.UTGÅENDE
    }

fun Mottaker.hentAdresse(): Adresse? =
    if (SamhandlerId(ident ?: "").gyldig()) {
        hentSamhandler(ident)?.adresse?.let { sa ->
            Adresse(
                adresselinje1 = sa.adresselinje1 ?: "",
                adresselinje2 = sa.adresselinje2,
                adresselinje3 = sa.adresselinje3,
                poststed = sa.poststed,
                postnummer = sa.postnummer,
                landkode = sa.land?.verdi?.let { alpha3LandkodeTilAlpha2(it) },
                landkode3 = sa.land?.verdi,
            )
        } ?: adresse
    } else {
        adresse
    }

fun Forsendelse.tilForsendelseRespons(dokumenterMetadata: Map<String, DokumentDtoMetadata>? = emptyMap()) =
    ForsendelseResponsTo(
        forsendelseId = forsendelseId!!,
        mottaker =
            this.mottaker?.let {
                MottakerTo(
                    ident = it.ident ?: "",
                    språk = it.språk,
                    navn = it.navn,
                    adresse =
                        it.hentAdresse()?.let { adresse ->
                            no.nav.bidrag.transport.dokument.forsendelse.MottakerAdresseTo(
                                adresselinje1 = adresse.adresselinje1,
                                adresselinje2 = adresse.adresselinje2,
                                adresselinje3 = adresse.adresselinje3,
                                bruksenhetsnummer = adresse.bruksenhetsnummer,
                                poststed = adresse.poststed,
                                postnummer = adresse.postnummer,
                                landkode = adresse.landkode,
                                landkode3 = adresse.landkode3,
                            )
                        },
                )
            },
        ettersendingsoppgave = this@tilForsendelseRespons.ettersendingsoppgave?.let { it.tilEttersendingsoppaveDto() },
        behandlingInfo =
            this.behandlingInfo?.let {
                BehandlingInfoResponseDto(
                    vedtakId = it.vedtakId,
                    behandlingId = it.behandlingId,
                    soknadId = it.soknadId,
                    behandlingType = it.toBehandlingType(),
                    erFattet = it.erFattetBeregnet != null || it.vedtakId != null,
                    barnIBehandling = it.barnIBehandling.toList(),
                )
            },
        gjelderIdent = this.gjelderIdent,
        arkivJournalpostId = this.journalpostIdFagarkiv,
        tittel = if (this.status == ForsendelseStatus.UNDER_OPPRETTELSE) tittel else this.dokumenter.hoveddokument?.tittel,
        tema = this.tema.name,
        saksnummer = this.saksnummer,
        forsendelseType =
            when (this.forsendelseType) {
                ForsendelseType.NOTAT -> ForsendelseTypeTo.NOTAT
                ForsendelseType.UTGÅENDE -> ForsendelseTypeTo.UTGÅENDE
            },
        status = this.tilForsendelseStatusTo(),
        opprettetDato = this.opprettetTidspunkt.toLocalDate(),
        dokumentDato = this.dokumentDato?.toLocalDate(),
        distribuertDato = this.distribuertTidspunkt?.toLocalDate(),
        enhet = this.enhet,
        opprettetAvIdent =
            if (opprettetAvIdent == "bisys") {
                "Bisys (Automatisk jobb)"
            } else if (opprettetAvIdent == FORSENDELSE_APP_ID) {
                "Bidrag (Automatisk jobb)"
            } else {
                opprettetAvIdent
            },
        opprettetAvNavn =
            if (opprettetAvIdent == "bisys") {
                "Bisys (Automatisk jobb)"
            } else if (opprettetAvIdent == FORSENDELSE_APP_ID) {
                "Bidrag (Automatisk jobb)"
            } else {
                opprettetAvNavn
            },
        dokumenter =
            this.dokumenter.ikkeSlettetSortertEtterRekkefølge.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    originalDokumentreferanse =
                        dokumenterMetadata?.get(it.dokumentreferanse)?.hentOriginalDokumentreferanse()
                            ?: it.dokumentreferanseOriginal,
                    originalJournalpostId =
                        dokumenterMetadata
                            ?.get(
                                it.dokumentreferanse,
                            )?.hentOriginalJournalpostId() ?: it.journalpostIdOriginal,
                    forsendelseId = it.forsendelseId?.toString(),
                    tittel = it.tittel,
                    journalpostId = it.journalpostId,
                    dokumentmalId = it.dokumentmalId,
                    arkivsystem = it.tilArkivSystemDto(),
                    redigeringMetadata = it.metadata.hentRedigeringmetadata(),
                    erSkjema = it.metadata.erSkjema(),
                    dokumentDato = it.dokumentDato,
                    status = it.tilDokumentStatusTo(),
                )
            },
    )

fun Ettersendingsoppgave.tilEttersendingsoppaveDto() =
    EttersendingsoppgaveDto(
        tittel = tittel,
        ettersendelseForJournalpostId = ettersendelseForJournalpostId,
        skjemaId = skjemaId,
        innsendingsfristDager = innsendingsfristDager,
        vedleggsliste =
            vedleggsliste.sortedBy { it.opprettetTidspunkt }.map { dokument ->
                EttersendingsoppgaveVedleggDto(
                    tittel = dokument.tittel,
                    id = dokument.id!!,
                    skjemaId = dokument.skjemaId,
                )
            },
    )
