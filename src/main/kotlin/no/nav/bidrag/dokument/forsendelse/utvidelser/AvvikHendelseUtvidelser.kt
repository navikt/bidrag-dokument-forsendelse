package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.transport.dokument.Avvikshendelse

fun Avvikshendelse.hentFagområde() = detaljer["fagomrade"]?.uppercase()
