package no.nav.bidrag.dokument.forsendelse.service

import no.nav.bidrag.dokument.forsendelse.model.DokumentRespons
import no.nav.bidrag.dokument.forsendelse.model.ForsendelseTypeTo
import no.nav.bidrag.dokument.forsendelse.model.OpprettForsendelseForespørsel
import no.nav.bidrag.dokument.forsendelse.model.OpprettForsendelseSvar
import no.nav.bidrag.dokument.forsendelse.model.tilArkivsystemDo
import no.nav.bidrag.dokument.forsendelse.model.tilDokumentDo
import no.nav.bidrag.dokument.forsendelse.model.tilDokumentStatusDo
import no.nav.bidrag.dokument.forsendelse.model.tilMottaker
import no.nav.bidrag.dokument.forsendelse.model.validerKanOppretteForsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Dokument
import no.nav.bidrag.dokument.forsendelse.persistence.entity.Forsendelse
import no.nav.bidrag.dokument.forsendelse.persistence.model.DokumentTilknyttetSom
import no.nav.bidrag.dokument.forsendelse.persistence.model.ForsendelseType
import no.nav.bidrag.dokument.forsendelse.persistence.repository.DokumentRepository
import no.nav.bidrag.dokument.forsendelse.persistence.repository.ForsendelseRepository
import org.springframework.stereotype.Component
import javax.transaction.Transactional


@Component
class OpprettForsendelseService(val forsendelseRepository: ForsendelseRepository, val dokumentRepository: DokumentRepository) {

    @Transactional
    fun opprettForsendelse(forespørsel: OpprettForsendelseForespørsel): OpprettForsendelseSvar {

        validerKanOppretteForsendelse(forespørsel)
        val forsendelse = opprettForsendelseFraForespørsel(forespørsel)

        val dokumenter = opprettDokumenter(forespørsel, forsendelse)

        return OpprettForsendelseSvar(
            forsendelseId = forsendelse.forsendelseId,
            dokumenter = dokumenter.map {
                DokumentRespons(
                    dokumentreferanse = it.dokumentreferanse,
                    tittel = it.tittel
                )
            }
        )
    }


    private fun opprettForsendelseFraForespørsel(forespørsel: OpprettForsendelseForespørsel): Forsendelse{

        val forsendelse = Forsendelse(
            saksnummer = forespørsel.saksnummer,
            forsendelseType = when(forespørsel.forsendelseTypeTo){
                ForsendelseTypeTo.UTGÅENDE -> ForsendelseType.UTGÅENDE
                ForsendelseTypeTo.NOTAT -> ForsendelseType.NOTAT
            },
            gjelderIdent = forespørsel.gjelderIdent,
            enhet = forespørsel.enhet,
            opprettetAvIdent = "",
            mottaker = forespørsel.mottaker?.tilMottaker()
        )

        return forsendelseRepository.save(forsendelse)
    }
    private fun opprettDokumenter(forespørsel: OpprettForsendelseForespørsel, forsendelse: Forsendelse): List<Dokument>{
        val dokumenter = forespørsel.dokumenter.mapIndexed { i, it ->
            it.tilDokumentDo(forsendelse, if (i == 0) DokumentTilknyttetSom.HOVEDDOKUMENT else DokumentTilknyttetSom.VEDLEGG)
        }

        return dokumentRepository.saveAll(dokumenter).toList()
    }
}