package no.nav.bidrag.dokument.forsendelse.utils

import jakarta.transaction.Transactional
import no.nav.bidrag.dokument.forsendelse.persistence.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.database.repository.DokumentRepository
import no.nav.bidrag.dokument.forsendelse.persistence.database.repository.ForsendelseRepository
import org.springframework.stereotype.Component

@Component
class TestDataManager(val forsendelseRepository: ForsendelseRepository, val dokumentRepository: DokumentRepository) {
    @Transactional
    fun hentForsendelse(forsendelseId: Long): Forsendelse? {
        return forsendelseRepository.findById(forsendelseId).get()
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun lagreForsendelse(forsendelseToSave: Forsendelse): Forsendelse {
        return forsendelseRepository.save(forsendelseToSave)
    }

    fun lagreForsendelseNotNewTransaction(forsendelseToSave: Forsendelse): Forsendelse {
        return forsendelseRepository.save(forsendelseToSave)
    }

    @OpprettForsendelseTestdataDsl
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun opprettOgLagreForsendelse(setup: ForsendelseBuilder.() -> Unit): Forsendelse {
        val forsendelseBuilder = ForsendelseBuilder()
        forsendelseBuilder.setup()
        return lagreForsendelse(forsendelseBuilder.build())
    }

    fun slettAlleData() {
        forsendelseRepository.deleteAll()
    }
}
