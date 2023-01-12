package no.nav.bidrag.dokument.forsendelse

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class TestContainerRunner: CommonTestRunner() {

    @Container
    private val postgreSqlDb = PostgreSQLContainer("postgres:latest").apply {
        withDatabaseName("bidrag-dokument-forsendelse")
        withUsername("sqluser1")
        withPassword("passord1")
        start()
    }
}