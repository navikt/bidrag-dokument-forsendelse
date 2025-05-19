package no.nav.bidrag.dokument.forsendelse.persistence.database.repository

import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

@Suppress("ktlint:standard:max-line-length")
interface ForsendelseRepository : CrudRepository<Forsendelse, Long> {
    @Query("SELECT f FROM forsendelse f WHERE f.unikReferanse = :unikReferanse and status not in ('AVBRUTT', 'SLETTET')")
    fun hentForsendelseMedUnikReferanse(unikReferanse: String): Forsendelse?

    @Query("select f from forsendelse f where f.saksnummer = :saksnummer")
    fun hentAlleMedSaksnummer(saksnummer: String): List<Forsendelse>

    @Query("select f from forsendelse f where f.behandlingInfo.soknadId = :soknadId")
    fun hentAlleMedSoknadId(soknadId: String): List<Forsendelse>

    //    @Query("select f from forsendelse f where f.forsendelseId = :forsendelseId and f.status <> 'AVBRUTT'")
    @Query("select f from forsendelse f where f.forsendelseId = :forsendelseId")
    fun medForsendelseId(forsendelseId: Long): Forsendelse?

    @Query(
        "select f from forsendelse f where f.status = 'DISTRIBUERT' and f.distribusjonKanal is null and f.distribuertTidspunkt <= :olderThan order by f.distribuertTidspunkt asc",
    )
    fun hentDistribuerteForsendelseUtenKanal(
        pageable: Pageable,
        olderThan: LocalDateTime,
    ): List<Forsendelse>

    @Query(
        "select * from forsendelse f where f.status = 'DISTRIBUERT' and f.distribusjon_kanal = 'NAV_NO' and f.distribuert_tidspunkt <= :beforeDate and f.distribuert_tidspunkt >= :afterDate " +
            "and (f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print' = :sjekketNavNoRedistribusjonTilSentralPrint or f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print' is null) order by f.distribuert_tidspunkt desc",
        nativeQuery = true,
    )
    fun hentDistribuerteForsendelseTilNAVNO(
        pageable: Pageable,
        beforeDate: LocalDateTime,
        afterDate: LocalDateTime,
        sjekketNavNoRedistribusjonTilSentralPrint: String = "false",
    ): List<Forsendelse>

    @Query(
        "select f from forsendelse f where f.status = 'FERDIGSTILT' and f.journalpostIdFagarkiv is not null and f.forsendelseType = 'UTGÅENDE' and (f.distribusjonKanal is null or f.distribusjonKanal != 'INGEN_DISTRIBUSJON')",
    )
    fun hentFerdigstilteArkivertIJoarkIkkeDistribuert(): List<Forsendelse>

    @Query(
        "select f from forsendelse f where (f.status = 'UNDER_PRODUKSJON' or f.status = 'FERDIGSTILT' ) " +
            "and (" +
            "(f.distribusjonKanal != 'INGEN_DISTRIBUSJON' and f.distribusjonKanal != 'SENTRAL_UTSKRIFT') " +
            "or f.distribusjonKanal is null " +
            "or (f.distribusjonKanal = 'SENTRAL_UTSKRIFT' and f.bestiltNyDistribusjon = false)" +
            ") " +
            "and f.forsendelseType = 'UTGÅENDE' and f.opprettetTidspunkt < current_date and exists (select 1 from f.dokumenter d where d.dokumentStatus = 'FERDIGSTILT' or d.dokumentStatus = 'KONTROLLERT')",
    )
    fun hentUnderProduksjonOpprettetFørDagensDato(): List<Forsendelse>

    @Query(
        "select f from forsendelse f where f.ettersendingsoppgave is not null and f.ettersendingsoppgave.innsendingsId is null and f.status = 'DISTRIBUERT' and f.journalpostIdFagarkiv is not null",
    )
    fun hentEttersendingerSomIkkeErOpprettet(): List<Forsendelse>
}
