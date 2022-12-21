package no.nav.bidrag.dokument.forsendelse.persistence.model

import no.nav.bidrag.dokument.forsendelse.persistence.entity.Forsendelse


val Forsendelse.hoveddokument get() = dokumenter.find { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }
val Forsendelse.vedlegger get() = dokumenter.filter { it.tilknyttetSom == DokumentTilknyttetSom.VEDLEGG }

val Forsendelse.hoveddokumentFørst get() = dokumenter.filter {it.slettetTidspunkt == null }.sortedByDescending { it.tilknyttetSom == DokumentTilknyttetSom.HOVEDDOKUMENT }

val Forsendelse.erAlleDokumenterFerdigstilt get() =  dokumenter.all { it.dokumentStatus == DokumentStatus.FERDIGSTILT }