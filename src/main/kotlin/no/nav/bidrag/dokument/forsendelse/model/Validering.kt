package no.nav.bidrag.dokument.forsendelse.model

fun MutableList<String>.validerErSann(
    verdi: Boolean,
    melding: String,
) {
    if (!verdi) {
        this.add(melding)
    }
}

fun MutableList<String>.validerIkkeNullEllerTom(
    verdi: String?,
    melding: String,
) {
    if (verdi.isNullOrEmpty()) {
        this.add(melding)
    }
}

fun MutableList<String>.validerIkkeNull(
    verdi: Any?,
    melding: String,
) {
    if (verdi == null) {
        this.add(melding)
    }
}

fun String?.isNotNullOrEmpty() = !this.isNullOrEmpty()
