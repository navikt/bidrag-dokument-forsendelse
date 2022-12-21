package no.nav.bidrag.dokument.forsendelse.persistence.repository

import no.nav.bidrag.dokument.forsendelse.persistence.entity.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Forsendelse
import org.springframework.data.repository.CrudRepository

interface DokumentRepository: CrudRepository<Dokument, Int> {

}