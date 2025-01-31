package no.nav.bidrag.dokument.forsendelse.persistence.database.repository

import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Ettersendingsoppgave
import org.springframework.data.repository.CrudRepository

interface VarselEttersendelseRepository : CrudRepository<Ettersendingsoppgave, Long>
