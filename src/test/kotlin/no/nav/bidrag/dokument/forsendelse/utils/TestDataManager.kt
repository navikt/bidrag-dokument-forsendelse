package no.nav.bidrag.dokument.forsendelse.utils

import no.nav.bidrag.dokument.forsendelse.database.datamodell.Forsendelse
import no.nav.bidrag.dokument.forsendelse.database.repository.DokumentRepository
import no.nav.bidrag.dokument.forsendelse.database.repository.ForsendelseRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Component
import javax.transaction.Transactional

@Component
class TestDataManager(val forsendelseRepository: ForsendelseRepository, val dokumentRepository: DokumentRepository){

    @Transactional
    fun hentForsendelse(forsendelseId: Long): Forsendelse? {
        val forsendelse = forsendelseRepository.medForsendelseId(forsendelseId)
//        forsendelse?.dokumenter?.let { Hibernate.initialize(it) }
        return forsendelse
    }
    fun lagreForsendelse(forsendelseToSave: Forsendelse): Forsendelse {
        return forsendelseRepository.save(forsendelseToSave)
    }

    @OpprettForsendelseTestdataDsl
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun opprettOgLagreForsendelse(setup: ForsendelseBuilder.() -> Unit): Forsendelse {
        val forsendelseBuilder = ForsendelseBuilder()
        forsendelseBuilder.setup()
        return lagreForsendelse(forsendelseBuilder.build())
    }
    fun slettAlleData(){
        forsendelseRepository.deleteAll()
    }
}