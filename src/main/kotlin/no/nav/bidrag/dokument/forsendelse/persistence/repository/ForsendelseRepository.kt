package no.nav.bidrag.dokument.forsendelse.persistence.repository

import no.nav.bidrag.dokument.forsendelse.persistence.entity.Forsendelse
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface ForsendelseRepository: CrudRepository<Forsendelse, Long> {

    @Query("select f from forsendelse f where f.saksnummer = :saksnummer")
    fun hentAlleMedSaksnummer(saksnummer: String): List<Forsendelse>

}