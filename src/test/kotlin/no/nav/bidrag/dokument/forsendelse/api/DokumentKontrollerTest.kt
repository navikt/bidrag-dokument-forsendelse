package no.nav.bidrag.dokument.forsendelse.api

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.bidrag.dokument.forsendelse.persistence.bucket.GcpCloudStorage
import no.nav.bidrag.dokument.forsendelse.utils.DOKUMENT_FIL
import org.junit.jupiter.api.BeforeEach

class DokumentKontrollerTest : KontrollerTestRunner() {

    @MockkBean
    lateinit var gcpCloudStorage: GcpCloudStorage

    @BeforeEach
    fun initGcpMock() {
        every { gcpCloudStorage.hentFil(any()) } returns DOKUMENT_FIL.toByteArray()
    }

}