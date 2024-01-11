package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.commons.service.finnPoststedForPostnummer

fun hentNorskPoststed(
    postnummer: String?,
    landkode: String?,
): String? {
    if (landkode != "NO" && landkode != "NOR") return null
    return postnummer?.let { finnPoststedForPostnummer(it) }
}
