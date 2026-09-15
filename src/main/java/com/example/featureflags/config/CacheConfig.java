package com.example.featureflags.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Duration;
import java.util.List;

/**
 * Transactions wrap cache operations (order = 0 makes the transaction the outer proxy),
 * and the transaction-aware proxy delays cache evictions until AFTER the DB commit.
 * This prevents a concurrent read from re-caching stale data before the write is committed.
 */
@Configuration
@EnableTransactionManagement(order = 0)
public class CacheConfig {

    public static final String EVAL_CACHE = "evaluations";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(60))   // safety net against stale entries
                .recordStats());
        manager.setCacheNames(List.of(EVAL_CACHE));
        return new TransactionAwareCacheManagerProxy(manager);
    }
}
