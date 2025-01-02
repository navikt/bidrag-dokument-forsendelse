package no.nav.bidrag.dokument.forsendelse

import mu.KotlinLogging
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

private val log = KotlinLogging.logger {}

@Testcontainers
@ActiveProfiles(value = ["test", "testcontainer"])
class TestContainerRunner : CommonTestRunner() {
    companion object {
        @Container
        protected val postgreSqlDb =
            PostgreSQLContainer("postgres:latest").apply {
                withDatabaseName("bidrag-dokument-forsendelse")
                withUsername("cloudsqliamuser")
                withPassword("admin")
                portBindings = listOf("7777:5432")
            }

        @Container
        protected val gcpCloudStorage =
            GenericContainer("fsouza/fake-gcs-server").apply {
                withExposedPorts(4443)
                withCreateContainerCmdModifier { cmd ->
                    cmd.withEntrypoint(
                        "/bin/fake-gcs-server",
                        "-scheme",
                        "http",
                    )
                }
            }

        private fun updateExternalUrlWithContainerUrl(fakeGcsExternalUrl: String) {
            val modifyExternalUrlRequestUri = "$fakeGcsExternalUrl/_internal/config"
            val updateExternalUrlJson = (
                "{" +
                    "\"externalUrl\": \"" + fakeGcsExternalUrl + "\"" +
                    "}"
            )
            val req =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(modifyExternalUrlRequestUri))
                    .header("Content-Type", "application/json")
                    .PUT(BodyPublishers.ofString(updateExternalUrlJson))
                    .build()
            val response =
                HttpClient
                    .newBuilder()
                    .build()
                    .send(req, BodyHandlers.discarding())
            if (response.statusCode() != 200) {
                throw RuntimeException(
                    "error updating fake-gcs-server with external url, response status code " + response.statusCode() + " != 200",
                )
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            postgreSqlDb.start()
            registry.add("spring.jpa.database") { "POSTGRESQL" }
            registry.add("spring.datasource.type") { "com.zaxxer.hikari.HikariDataSource" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.locations") { "classpath:/db/migration" }
            registry.add("spring.datasource.url", postgreSqlDb::getJdbcUrl)
            registry.add("spring.datasource.password", postgreSqlDb::getPassword)
            registry.add("spring.datasource.username", postgreSqlDb::getUsername)
            gcpCloudStorage.start()
            try {
                val url = "http://${gcpCloudStorage.host}:${gcpCloudStorage.firstMappedPort}"
                updateExternalUrlWithContainerUrl(url)
                log.info { "Setter GCP_HOST miljøvariabel til $url" }
                registry.add("GCP_HOST") { url }
            } catch (e: Exception) {
                log.error(e) { "Det skjedde en feil ved oppdatering av GCP_HOST variabel" }
            }
        }
    }
}
