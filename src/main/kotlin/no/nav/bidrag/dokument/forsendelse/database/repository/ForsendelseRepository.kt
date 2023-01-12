package no.nav.bidrag.dokument.forsendelse.database.repository

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface ForsendelseRepository: CrudRepository<Forsendelse, Long> {

    @Query("select f from forsendelse f where f.saksnummer = :saksnummer")
    fun hentAlleMedSaksnummer(saksnummer: String): List<Forsendelse>
//    @Query("select f from forsendelse f where f.forsendelseId = :forsendelseId and f.status <> 'AVBRUTT'")
    @Query("select f from forsendelse f where f.forsendelseId = :forsendelseId")
    fun medForsendelseId(forsendelseId: Long): Forsendelse?
}