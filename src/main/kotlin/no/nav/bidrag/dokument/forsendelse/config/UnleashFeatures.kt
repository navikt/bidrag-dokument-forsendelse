package no.nav.bidrag.dokument.forsendelse.config

import io.getunleash.variant.Variant
import no.nav.bidrag.commons.unleash.UnleashFeaturesProvider

enum class UnleashFeatures(
    val featureName: String,
    defaultValue: Boolean,
) {
    DOKUMENTVALG_FRA_VEDTAK_BEHANDLING("forsendelse.dokumentvalg_vedtak_behandling", false),
    OPPRETT_BATCHBREV("forsendelse.opprett_batchbrev", false),
    VIS_BATCHBREV_NYERE_ENN_3_DAGER("forsendelse.batchbrev_nyere_enn_3_dager", false),
    ;

    private var defaultValue = false

    init {
        this.defaultValue = defaultValue
    }

    val isEnabled: Boolean
        get() = UnleashFeaturesProvider.isEnabled(feature = featureName, defaultValue = defaultValue)

    val variant: Variant?
        get() = UnleashFeaturesProvider.getVariant(featureName)
}
