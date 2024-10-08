package no.nav.bidrag.dokument.forsendelse.persistence.database.repository

import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface DokumentRepository : CrudRepository<Dokument, Long> {
    @Query("select d from dokument d where d.dokumentreferanseOriginal = :dokumentreferanse or d.dokumentId = :dokumentId")
    fun hentDokumenterMedDokumentreferanse(
        dokumentreferanse: String,
        dokumentId: Long?,
    ): List<Dokument>

    fun findByDokumentId(dokumentId: Long?): Dokument?

    @Query(
        "select d from dokument d where d.dokumentStatus = 'BESTILLING_FEILET' " +
            "and d.slettetTidspunkt is null and d.forsendelse.status = 'UNDER_PRODUKSJON'",
    )
    fun hentDokumenterSomHarStatusBestillingFeilet(): List<Dokument>

    @Query(
        "select d from dokument d where d.dokumentStatus = 'UNDER_PRODUKSJON' " +
            "and d.slettetTidspunkt is null " +
            "and d.forsendelse.status = 'UNDER_PRODUKSJON'",
    )
    fun hentDokumenterSomHarStatusUnderProduksjon(): List<Dokument>

    @Query(
        "select d from dokument d where d.forsendelse.status = 'UNDER_PRODUKSJON' and d.slettetTidspunkt is null " +
            "and d.dokumentStatus = 'UNDER_REDIGERING' " +
            "and d.arkivsystem != 'FORSENDELSE' and d.opprettetTidspunkt <= :beforeDate " +
            "and d.opprettetTidspunkt > :afterDate order by d.opprettetTidspunkt desc",
    )
    fun hentDokumentIkkeFerdigstiltFørDato(
        pageable: Pageable,
        beforeDate: LocalDateTime,
        afterDate: LocalDateTime,
    ): List<Dokument>
}
