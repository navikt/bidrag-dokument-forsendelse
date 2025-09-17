package no.nav.bidrag.dokument.forsendelse.hendelse

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import no.nav.bidrag.commons.CorrelationId
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.dokument.forsendelse.config.UnleashFeatures
import no.nav.bidrag.dokument.forsendelse.consumer.BidragDokumentBestillingConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.BidragVedtakConsumer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentBestillingForespørsel
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalType
import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentmalInnholdType
import no.nav.bidrag.dokument.forsendelse.consumer.dto.MottakerAdresseTo
import no.nav.bidrag.dokument.forsendelse.consumer.dto.MottakerTo
import no.nav.bidrag.dokument.forsendelse.mapper.hentAdresse
import no.nav.bidrag.dokument.forsendelse.model.DokumentBestilling
import no.nav.bidrag.dokument.forsendelse.model.KunneIkkBestilleDokument
import no.nav.bidrag.dokument.forsendelse.model.Saksbehandler
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.BehandlingInfo
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentArkivSystem
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.DokumentStatus
import no.nav.bidrag.dokument.forsendelse.persistence.database.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.persistence.database.repository.ForsendelseRepository
import no.nav.bidrag.dokument.forsendelse.service.SaksbehandlerInfoManager
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.erStatiskDokument
import no.nav.bidrag.dokument.forsendelse.utvidelser.hentDokument
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.dokument.DokumentArkivSystemDto
import no.nav.bidrag.transport.dokument.DokumentHendelse
import no.nav.bidrag.transport.dokument.DokumentHendelseType
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Component
class DokumentBestillingLytter(
    val dokumentBestillingKonsumer: BidragDokumentBestillingConsumer,
    val vedtakConsumer: BidragVedtakConsumer,
    val forsendelseRepository: ForsendelseRepository,
    val dokumentTjeneste: DokumentTjeneste,
    val dokumentKafkaHendelseProdusent: DokumentKafkaHendelseProdusent,
    val saksbehandlerInfoManager: SaksbehandlerInfoManager,
    val meterRegistry: MeterRegistry,
) {
    @Suppress("ktlint:standard:property-naming")
    private val DOKUMENTMAL_COUNTER_NAME = "forsendelse_dokumentmal_opprettet"

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun bestill(dokumentBestilling: DokumentBestilling) {
        val (forsendelseId, dokumentreferanse) = dokumentBestilling
        val forsendelse =
            forsendelseRepository.medForsendelseId(forsendelseId)
                ?: throw KunneIkkBestilleDokument("Fant ikke forsendelse $forsendelseId")
        val dokument =
            forsendelse.dokumenter.hentDokument(dokumentreferanse)
                ?: throw KunneIkkBestilleDokument(
                    "Fant ikke dokument med dokumentreferanse $dokumentreferanse i forsendelse ${forsendelse.forsendelseId}",
                )
        if (dokument.dokumentmalId.isNullOrEmpty()) {
            throw KunneIkkBestilleDokument(
                "Dokument med dokumentreferanse $dokumentreferanse mangler dokumentmalId",
            )
        }

        try {
            if (erStatiskDokument(dokument.dokumentmalId)) {
                oppdaterStatusForStatiskDokument(dokument)
                measureBestilling(forsendelse, dokument)
                return
            }
            if (erFraDokumentProduksjon(dokument.dokumentmalId)) {
                oppdaterStatusForStatiskDokument(dokument)
                measureBestilling(forsendelse, dokument)
                return
            }

            val arkivSystem = sendBestilling(forsendelse, dokument)
            measureBestilling(forsendelse, dokument)
            dokumentTjeneste.lagreDokument(
                dokument.copy(
                    arkivsystem =
                        when (arkivSystem) {
                            DokumentArkivSystemDto.MIDLERTIDLIG_BREVLAGER -> DokumentArkivSystem.MIDLERTIDLIG_BREVLAGER
                            else -> DokumentArkivSystem.UKJENT
                        },
                    metadata =
                        run {
                            val metadata = dokument.metadata
                            metadata.lagreBestiltTidspunkt(LocalDateTime.now())
                            metadata.inkrementerBestiltAntallGanger()
                            metadata.copy()
                        },
                    dokumentStatus = DokumentStatus.UNDER_PRODUKSJON,
                ),
            )
        } catch (e: Exception) {
            dokumentTjeneste.lagreDokument(
                dokument.copy(
                    dokumentStatus = DokumentStatus.BESTILLING_FEILET,
                    metadata =
                        run {
                            val metadata = dokument.metadata
                            metadata.lagreBestiltTidspunkt(LocalDateTime.now())
                            metadata.inkrementerBestiltAntallGanger()
                            metadata.copy()
                        },
                ),
            )
            LOGGER.error(e) {
                "Det skjedde en feil ved bestilling av dokumentmal ${dokument.dokumentmalId} " +
                    "for dokumentreferanse $dokumentreferanse og forsendelseId $forsendelseId: ${e.message}"
            }
        }
    }

    private fun oppdaterStatusForStatiskDokument(dokument: Dokument) {
        if (erRedigerbar(dokument.dokumentmalId!!)) {
            LOGGER.info {
                "Dokument med mal ${dokument.dokumentmalId} og tittel ${dokument.tittel} " +
                    "knyttet til forsendelse ${dokument.forsendelseId} " +
                    "er statisk vedlegg som er redigerbar. Setter status til MÅ_KONTROLLERES."
            }
            dokumentTjeneste.lagreDokument(
                dokument.copy(
                    arkivsystem = DokumentArkivSystem.BIDRAG,
                    dokumentStatus = DokumentStatus.MÅ_KONTROLLERES,
                    metadata =
                        run {
                            val metadata = dokument.metadata
                            metadata.markerSomStatiskDokument()
                            if (erSkjema(dokument.dokumentmalId)) metadata.markerSomSkjema()
                            metadata.copy()
                        },
                ),
            )
        } else {
            LOGGER.info {
                "Dokument med mal ${dokument.dokumentmalId} og tittel ${dokument.tittel} " +
                    "knyttet til forsendelse ${dokument.forsendelseId} er statisk vedlegg. Ferdigstiller dokument."
            }
            dokumentTjeneste.lagreDokument(
                dokument.copy(
                    arkivsystem = DokumentArkivSystem.BIDRAG,
                    dokumentStatus = DokumentStatus.FERDIGSTILT,
                    ferdigstiltTidspunkt = LocalDateTime.now(),
                    ferdigstiltAvIdent = TokenUtils.hentSaksbehandlerIdent(),
                    metadata =
                        run {
                            val metadata = dokument.metadata
                            metadata.markerSomStatiskDokument()
                            metadata.copy()
                        },
                ),
            )
        }
    }

    private fun sendBestilling(
        forsendelse: Forsendelse,
        dokument: Dokument,
    ): DokumentArkivSystemDto? {
        val dokumentMalId = dokument.dokumentmalId!!

        if (forsendelse.kanBestillesFraBidragDokumentBestilling(dokumentMalId)) {
            val bestilling = tilForespørsel(forsendelse, dokument)
            val respons = dokumentBestillingKonsumer.bestill(bestilling, dokument.dokumentmalId)
            LOGGER.info {
                "Bestilte ny dokument med mal ${dokument.dokumentmalId} og tittel ${bestilling.tittel} " +
                    "for dokumentreferanse ${bestilling.dokumentreferanse}. Dokument er satt til å bli ferdigstilt automatisk ${dokument.ferdigstill}. Dokumentet er arkivert i ${respons?.arkivSystem?.name}"
            }
            return respons?.arkivSystem
        } else {
            // Bisys lytter på kafka melding og produserer brev. Dette trigger deretter kvittering fra brevserver som bidrag-dokument-forsendelse lytter på og oppdaterer status når brevet er produsert og klar for redigering
            LOGGER.info {
                "Sender bestilling av nytt dokument med mal ${dokument.dokumentmalId} og tittel ${dokument.tittel} " +
                    "for dokumentreferanse ${dokument.dokumentreferanse} " +
                    "som Kafka melding. Venter på at Bisys oppretter brevet og kvittering sendt for produkjson av brev."
            }
            dokumentKafkaHendelseProdusent.publiser(tilKafkaMelding(forsendelse, dokument))
        }
        return null
    }

    private fun measureBestilling(
        forsendelse: Forsendelse,
        dokument: Dokument,
    ) {
        try {
            val dokumentMalId = dokument.dokumentmalId!!
            meterRegistry
                .counter(
                    DOKUMENTMAL_COUNTER_NAME,
                    "dokumentMalId",
                    dokumentMalId,
                    "type",
                    forsendelse.forsendelseType.name,
                    "spraak",
                    dokument.språk ?: forsendelse.språk,
                    "enhet",
                    forsendelse.enhet,
                    "tema",
                    forsendelse.tema.name,
                    "tittel",
                    if (dokumentMalId == "BI01S02") {
                        "Fritekstbrev"
                    } else if (forsendelse.forsendelseType == ForsendelseType.NOTAT) {
                        "Notat"
                    } else {
                        dokument.tittel
                    },
                    "er_statisk_dokument",
                    if (dokument.erStatiskDokument()) "true" else "false",
                ).increment()
        } catch (e: Exception) {
            LOGGER.warn(e) { "Det skjedde en feil ved måling av bestilt dokumentmal for forsendelse ${forsendelse.forsendelseId}" }
        }
    }

    private fun tilKafkaMelding(
        forsendelse: Forsendelse,
        dokument: Dokument,
    ): DokumentHendelse =
        DokumentHendelse(
            hendelseType = DokumentHendelseType.BESTILLING,
            forsendelseId = forsendelse.forsendelseId.toString(),
            dokumentreferanse = dokument.dokumentreferanse,
            sporingId = CorrelationId.fetchCorrelationIdForThread(),
        )

    private fun Forsendelse.kanBestillesFraBidragDokumentBestilling(dokumentMal: String): Boolean {
        val dokumentDetaljer =
            dokumentBestillingKonsumer.dokumentmalDetaljer()[dokumentMal]
                ?: DokumentMalDetaljer(tittel = "", type = DokumentMalType.UTGÅENDE)
        val erFattetGjennomNyLøsning =
            behandlingInfo?.behandlingId != null && behandlingInfo?.vedtakId != null
        val erOpprettetGjennomNyLøsning = behandlingInfo?.behandlingId != null
        val erAldersjusteringFattetGjennomNyLøsning = behandlingInfo.erAldersjusteringFattetGjennomNyLøsning()
        return dokumentDetaljer.kanBestilles ||
            erFattetGjennomNyLøsning ||
            dokumentDetaljer.kreverBehandling &&
            erOpprettetGjennomNyLøsning ||
            erAldersjusteringFattetGjennomNyLøsning
    }

    private fun BehandlingInfo?.erAldersjusteringFattetGjennomNyLøsning(): Boolean =
        this?.let {
            if (vedtakType == Vedtakstype.ALDERSJUSTERING && vedtakId != null) {
                val vedtak = vedtakConsumer.hentVedtak(vedtakId)
                vedtak?.kildeapplikasjon != "bisys"
            } else {
                false
            }
        } ?: false

    private fun erFraDokumentProduksjon(dokumentMal: String): Boolean =
        dokumentBestillingKonsumer.dokumentmalDetaljer()[dokumentMal]?.nyDokumentProduksjon ?: false

    private fun erStatiskDokument(dokumentMal: String): Boolean =
        dokumentBestillingKonsumer.dokumentmalDetaljer()[dokumentMal]?.statiskInnhold ?: false

    private fun erRedigerbar(dokumentMal: String): Boolean =
        dokumentBestillingKonsumer.dokumentmalDetaljer()[dokumentMal]?.redigerbar ?: false

    private fun erSkjema(dokumentMal: String): Boolean =
        dokumentBestillingKonsumer.dokumentmalDetaljer()[dokumentMal]?.innholdType == DokumentmalInnholdType.SKJEMA

    private fun tilForespørsel(
        forsendelse: Forsendelse,
        dokument: Dokument,
    ): DokumentBestillingForespørsel {
        val saksbehandlerIdent = if (saksbehandlerInfoManager.erApplikasjonBruker()) forsendelse.opprettetAvIdent else null
        val erBatchbrev =
            dokument.ferdigstill ||
                forsendelse.opprettetAvIdent.startsWith("bidrag-automatisk-jobb") ||
                UnleashFeatures.OPPRETT_BATCHBREV.isEnabled
        return DokumentBestillingForespørsel(
            erBatchBrev = erBatchbrev,
            dokumentreferanse = dokument.dokumentreferanse,
            saksnummer = forsendelse.saksnummer,
            tittel = dokument.tittel,
            gjelderId = forsendelse.gjelderIdent,
            enhet = forsendelse.enhet,
            vedtakId = forsendelse.behandlingInfo?.vedtakId,
            behandlingId = forsendelse.behandlingInfo?.behandlingId,
            språk = dokument.språk ?: forsendelse.språk,
            saksbehandler = saksbehandlerIdent?.let { Saksbehandler(it) },
            barnIBehandling = forsendelse.behandlingInfo?.barnIBehandling?.toList() ?: emptyList(),
            mottaker =
                forsendelse.mottaker?.let { mottaker ->
                    MottakerTo(
                        mottaker.ident,
                        mottaker.navn,
                        mottaker.språk,
                        adresse =
                            mottaker.hentAdresse()?.let {
                                MottakerAdresseTo(
                                    adresselinje1 = it.adresselinje1,
                                    adresselinje2 = it.adresselinje2,
                                    adresselinje3 = it.adresselinje3,
                                    bruksenhetsnummer = it.bruksenhetsnummer,
                                    postnummer = it.postnummer,
                                    landkode = it.landkode,
                                    landkode3 = it.landkode3,
                                    poststed = it.poststed,
                                )
                            },
                    )
                },
        )
    }
}
