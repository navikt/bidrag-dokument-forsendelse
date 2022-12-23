package no.nav.bidrag.dokument.forsendelse.konfigurasjon

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
@Profile(value = ["!test"]) // Ignore cache on tests
class CacheConfig {
    companion object {
        const val DOKUMENTMALER_CACHE = "DOKUMENTMALER_CACHE"
        const val SAKSBEHANDLERINFO_CACHE = "SAKSBEHANDLERINFO_CACHE"
        const val PERSON_CACHE = "PERSON_CACHE"
    }
    @Bean
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.registerCustomCache(DOKUMENTMALER_CACHE, Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).build())
        return caffeineCacheManager;
    }

}