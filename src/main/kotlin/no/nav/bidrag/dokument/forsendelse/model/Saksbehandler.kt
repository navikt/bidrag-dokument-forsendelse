package no.nav.bidrag.dokument.forsendelse.model


data class Saksbehandler(
    val ident: String? = null,
    val navn: String? = null
) {
    val fornavnEtternavn: String get() = if (navn.isNullOrEmpty()) navn ?: "" else {
                val navnDeler = navn.split(",\\s*".toRegex(), limit = 2).toTypedArray()
                if (navnDeler.size > 1) {
                    navnDeler[1] + " " + navnDeler[0]
                } else navn
            }
}
