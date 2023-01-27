package no.nav.bidrag.dokument.forsendelse.utvidelser

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.model.ForsendelseType

val Forsendelse.erNotat get() = forsendelseType == ForsendelseType.NOTAT
val Forsendelse.erUtgående get() = forsendelseType == ForsendelseType.UTGÅENDE
val Forsendelse.forsendelseIdMedPrefix get() = "BIF-$forsendelseId"