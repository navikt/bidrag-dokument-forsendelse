package no.nav.bidrag.dokument.forsendelse.database.model

import java.time.LocalDate

data class KodeverkResponse(
    var betydninger: Map<String, List<KodeverkBetydning>>
) {
    fun hentFraKode(kode: String?) = betydninger[kode]?.get(0)
}

data class KodeverkBetydning(
    var gyldigFra: LocalDate,
    var gyldigTil: LocalDate,
    var beskrivelser: Map<String, KodeverkBeskrivelse>
) {
    fun hentNorskNavn() = if (beskrivelser["nb"]?.tekst.isNullOrEmpty()) beskrivelser["nb"]?.term else beskrivelser["nb"]?.tekst
}

data class KodeverkBeskrivelse(
    var term: String,
    var tekst: String
)