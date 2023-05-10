package no.nav.bidrag.dokument.forsendelse.database.repository

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface ForsendelseRepository : CrudRepository<Forsendelse, Long> {

    @Query("select f from forsendelse f where f.saksnummer = :saksnummer")
    fun hentAlleMedSaksnummer(saksnummer: String): List<Forsendelse>

    //    @Query("select f from forsendelse f where f.forsendelseId = :forsendelseId and f.status <> 'AVBRUTT'")
    @Query("select f from forsendelse f where f.forsendelseId = :forsendelseId")
    fun medForsendelseId(forsendelseId: Long): Forsendelse?

    @Query("select f from forsendelse f where f.status = 'DISTRIBUERT' and f.distribusjonKanal is null and f.distribuertTidspunkt <= :olderThan order by f.distribuertTidspunkt asc")
    fun hentDistribuerteForsendelseUtenKanal(pageable: Pageable, olderThan: LocalDateTime): List<Forsendelse>

    @Query("select f from forsendelse f where f.status = 'FERDIGSTILT' and f.journalpostIdFagarkiv is not null and f.forsendelseType = 'UTGÅENDE' and f.distribusjonKanal is null ")
    fun hentFerdigstilteIkkeDistribuert(): List<Forsendelse>
}
