package com.aerovhyn.api.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import java.time.Duration;

@Configuration
public class CacheConfig {
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                .withCacheConfiguration("hospitals",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(10)));
    }
}
