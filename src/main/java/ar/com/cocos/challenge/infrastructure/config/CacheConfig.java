package ar.com.cocos.challenge.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuración de Caffeine Cache.
 *
 * TTL por cache:
 * - instrument-search: 10 min (catálogo cambia poco)
 * - latest-market-prices: 1 min (precios deben estar frescos)
 * - all-instruments: 10 min (validaciones)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final long MAX_SIZE = 1_000;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache("instrument-search", cache(10));
        cacheManager.registerCustomCache("latest-market-prices", cache(1));
        cacheManager.registerCustomCache("all-instruments", cache(10));
        return cacheManager;
    }

    private Cache<Object, Object> cache(long ttlMinutes) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(MAX_SIZE)
                .recordStats()
                .build();
    }
}
