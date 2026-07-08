package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.domene.enums.behandling.Behandlingstype
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.BehandlingDetaljerGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.SamværsperiodeGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.SøknadGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.VirkningstidspunktGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.filtrerOgKonverterBasertPåEgenReferanse
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.dokument.Avvikshendelse
import no.nav.bidrag.transport.felles.toLocalDate

fun Avvikshendelse.hentFagområde() = detaljer["fagomrade"]?.uppercase()

internal fun List<GrunnlagDto>.hentBehandlingDetaljer(): BehandlingDetaljerGrunnlag? =
    filtrerOgKonverterBasertPåEgenReferanse<BehandlingDetaljerGrunnlag>(Grunnlagstype.BEHANDLING_DETALJER).firstOrNull()?.innhold

internal fun List<GrunnlagDto>.hentSøknader(gjelderReferanse: String? = null): List<SøknadGrunnlag> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.SØKNAD)
        .filter {
            gjelderReferanse.isNullOrEmpty() || it.gjelderBarnReferanse == gjelderReferanse || it.gjelderReferanse == gjelderReferanse
        }.map { it.innholdTilObjekt<SøknadGrunnlag>() }

val Behandlingstype.erForholdsmessigFordeling get() =
    listOf(
        Behandlingstype.FORHOLDSMESSIG_FORDELING,
        Behandlingstype.FORHOLDSMESSIG_FORDELING_KLAGE,
    ).contains(this)

internal fun List<GrunnlagDto>.hentSamvær(gjelderReferanse: String): List<SamværsperiodeGrunnlag> =
    filtrerBasertPåEgenReferanse(Grunnlagstype.SAMVÆRSPERIODE)
        .filter {
            it.gjelderBarnReferanse == gjelderReferanse || it.gjelderReferanse == gjelderReferanse
        }.map { it.innholdTilObjekt<SamværsperiodeGrunnlag>() }

fun List<GrunnlagDto>.harOpprettetForholdsmessigFordeling(): Boolean =
    hentBehandlingDetaljer()?.opprettetForholdsmessigFordeling == true ||
        // Opprettet FF
        hentSøknader().any {
            it.behandlingstype?.erForholdsmessigFordeling == true
        } ||
        // Opprett FF når alle barna er i samme søknad. Tilfelle hvor det er valgt ulik virkningstidspunkt for barna
        filtrerOgKonverterBasertPåEgenReferanse<VirkningstidspunktGrunnlag>(Grunnlagstype.VIRKNINGSTIDSPUNKT).any {
            if (it.gjelderBarnReferanse == null) return@any false
            val minsteSamværsperiode = hentSamvær(it.gjelderBarnReferanse!!).minOfOrNull { it.periode.fom } ?: return@any false
            it.innhold.virkningstidspunkt < minsteSamværsperiode.toLocalDate()
        } ||
        // Flere søknader = Opprettet FF vanligvis
        hentSøknader().size > 1
