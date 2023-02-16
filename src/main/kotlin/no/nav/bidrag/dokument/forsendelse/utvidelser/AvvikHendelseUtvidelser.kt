package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.dto.Avvikshendelse

fun Avvikshendelse.hentFagområde() = detaljer["fagomrade"]?.uppercase()