@file:Suppress("ktlint:standard:filename")

package no.nav.bidrag.dokument.forsendelse.model

import no.nav.bidrag.dokument.forsendelse.consumer.dto.DokumentMalDetaljer

data class HentDokumentValgResponse(
    val dokumentMalDetaljer: Map<String, DokumentMalDetaljer>,
    val automatiskOpprettDokumenter: List<DokumentMalDetaljer> = emptyList(),
)
