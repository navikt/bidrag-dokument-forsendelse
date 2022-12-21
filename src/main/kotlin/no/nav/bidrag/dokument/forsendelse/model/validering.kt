package no.nav.bidrag.dokument.forsendelse.model



fun MutableList<String>.validerErSann(verdi: Boolean, melding: String){
    if (!verdi){
        this.add(melding)
    }
}
fun validerKanOppretteForsendelse(forespørsel: OpprettForsendelseForespørsel){
    val feilmeldinger = mutableListOf<String>()

    forespørsel.dokumenter.forEachIndexed {i, it->
        feilmeldinger.validerErSann(it.tittel.isNotEmpty(), "Tittel på dokument $i kan ikke være tom")
        if (!it.dokumentreferanse.isNullOrEmpty() || !it.journalpostId.isNullOrEmpty()){
            feilmeldinger.validerErSann(!it.dokumentreferanse.isNullOrEmpty() && !it.journalpostId.isNullOrEmpty(), "Både journalpostid og dokumentreferanse må settes hvis en av dem er satt journalpostid=${it.journalpostId} og dokumentreferanse=${it.dokumentreferanse}.")
        }
    }

    if (feilmeldinger.isNotEmpty()){
        throw UgyldigForespørsel(feilmeldinger.joinToString(", "))
    }
}

fun validerKanOppdatereForespørsel(forespørsel: OppdaterForsendelseForespørsel){

}