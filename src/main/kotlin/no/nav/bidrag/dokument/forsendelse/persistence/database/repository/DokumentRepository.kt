package no.nav.bidrag.dokument.forsendelse.persistence.database.repository

import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Dokument
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface DokumentRepository : CrudRepository<Dokument, Long> {

    @Query("select d from dokument d where d.dokumentreferanseOriginal = :dokumentreferanse or d.dokumentId = :dokumentId")
    fun hentDokumenterMedDokumentreferanse(dokumentreferanse: String, dokumentId: Long?): List<Dokument>

    @Query("select d from dokument d where d.dokumentStatus = 'BESTILLING_FEILET' and d.forsendelse.status = 'UNDER_PRODUKSJON'")
    fun hentDokumenterSomHarStatusBestillingFeilet(): List<Dokument>
}
