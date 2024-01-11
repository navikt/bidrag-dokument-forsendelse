package no.nav.bidrag.dokument.forsendelse.model

import com.neovisionaries.i18n.CountryCode

fun alpha3LandkodeTilAlpha2(landkode: String?): String? =
    if (landkode == null) {
        null
    } else if ("XXK" == landkode) {
        "XK"
    } else {
        CountryCode.getByAlpha3Code(landkode)?.alpha2
    }
