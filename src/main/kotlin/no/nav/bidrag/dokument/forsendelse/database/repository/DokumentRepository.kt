package no.nav.bidrag.dokument.forsendelse.database.repository

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Dokument
import org.springframework.data.repository.CrudRepository

interface DokumentRepository: CrudRepository<Dokument, Int> {

}