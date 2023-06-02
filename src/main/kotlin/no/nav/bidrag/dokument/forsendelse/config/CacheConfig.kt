package no.nav.bidrag.dokument.forsendelse.config

import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.bidrag.commons.cache.EnableUserCache
import no.nav.bidrag.commons.cache.InvaliderCacheFørStartenAvArbeidsdag
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
@EnableUserCache
class CacheConfig {
    companion object {
        const val PERSON_CACHE = "PERSON_CACHE"
        const val SAK_CACHE = "SAK_CACHE"
        const val PERSON_SPRAAK_CACHE = "PERSON_SPRAAK_CACHE"
        const val DOKUMENTMALER_CACHE = "DOKUMENTMALER_CACHE"
        const val DOKUMENTMADETALJER_CACHE = "DOKUMENTMADETALJER_CACHE"
        const val SAKSBEHANDLERINFO_CACHE = "SAKSBEHANDLERINFO_CACHE"
        const val TILGANG_SAK_CACHE = "TILGANG_SAK_CACHE"
        const val VEDTAK_CACHE = "VEDTAK_CACHE"
        const val TILGANG_PERSON_CACHE = "TILGANG_PERSON_CACHE"
        const val TILGANG_TEMA_CACHE = "TILGANG_TEMA_CACHE"
    }

    @Bean
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.registerCustomCache(
            PERSON_SPRAAK_CACHE,
            Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build()
        )
        caffeineCacheManager.registerCustomCache(PERSON_CACHE, Caffeine.newBuilder().expireAfter(InvaliderCacheFørStartenAvArbeidsdag()).build())
        caffeineCacheManager.registerCustomCache(DOKUMENTMADETALJER_CACHE, Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).build())
        caffeineCacheManager.registerCustomCache(TILGANG_TEMA_CACHE, Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build())
        caffeineCacheManager.registerCustomCache(TILGANG_PERSON_CACHE, Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build())
        caffeineCacheManager.registerCustomCache(TILGANG_SAK_CACHE, Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build())
        caffeineCacheManager.registerCustomCache(VEDTAK_CACHE, Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build())
        caffeineCacheManager.registerCustomCache(SAK_CACHE, Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build())
        return caffeineCacheManager
    }
}
