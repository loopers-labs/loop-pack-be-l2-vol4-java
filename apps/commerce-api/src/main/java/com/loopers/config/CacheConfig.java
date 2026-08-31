package com.loopers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.product.ProductCacheDto;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

// TX(Ordered.LOWEST_PRECEDENCE)보다 한 단계 outer — TX 커밋 후 @CacheEvict 보장
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
@Configuration
public class CacheConfig {

    // 필드 타입 변경 시 버전 올릴 것 — 기존 캐시 자연 소멸(TTL) 후 새 버전으로 재적재
    public static final String PRODUCT_CACHE = "product:v1";
    public static final String RANKING_WEIGHT_CACHE = "ranking-weight:v1";
    public static final String WEEKLY_RANKING_CACHE = "weekly-ranking:v1";
    public static final String MONTHLY_RANKING_CACHE = "monthly-ranking:v1";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 타입 고정 직렬화 — @class 미포함, 클래스 이동/이름 변경에도 역직렬화 안전
        Jackson2JsonRedisSerializer<ProductCacheDto> productSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, ProductCacheDto.class);

        // likeCount는 이벤트 기반 비동기 집계라 evict-then-stale-put 레이스 위험이 있음 —
        // TTL을 짧게 잡아 최악의 경우에도 캐시가 금방 자연 치유되도록 한다 (완벽한 정합성 대신 감수).
        RedisCacheConfiguration productCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(3))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(productSerializer));

        RedisCacheConfiguration weightCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        RedisCacheConfiguration mvRankingCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(PRODUCT_CACHE, productCacheConfig)
                .withCacheConfiguration(RANKING_WEIGHT_CACHE, weightCacheConfig)
                .withCacheConfiguration(WEEKLY_RANKING_CACHE, mvRankingCacheConfig)
                .withCacheConfiguration(MONTHLY_RANKING_CACHE, mvRankingCacheConfig)
                .build();
    }
}
