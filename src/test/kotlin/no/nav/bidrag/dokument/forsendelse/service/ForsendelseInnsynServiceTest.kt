package no.nav.bidrag.dokument.forsendelse.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.bidrag.dokument.forsendelse.service.dao.DokumentTjeneste
import no.nav.bidrag.dokument.forsendelse.service.dao.ForsendelseTjeneste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class ForsendelseInnsynServiceTest {

    @MockkBean
    lateinit var forsendelseTjeneste: ForsendelseTjeneste

    @MockkBean
    lateinit var tilgangskontrollService: TilgangskontrollService

    @MockkBean
    lateinit var dokumentValgService: DokumentValgService

    @MockkBean
    lateinit var dokumentTjeneste: DokumentTjeneste

    lateinit var forsendelseInnsynService: ForsendelseInnsynService

    @BeforeEach
    fun init() {
        forsendelseInnsynService = ForsendelseInnsynService(
            forsendelseTjeneste,
            tilgangskontrollService,
            dokumentValgService,
            dokumentTjeneste
        )
        every { tilgangskontrollService.sjekkTilgangForsendelse(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangSak(any()) } returns Unit
        every { tilgangskontrollService.sjekkTilgangPerson(any()) } returns Unit
    }

}