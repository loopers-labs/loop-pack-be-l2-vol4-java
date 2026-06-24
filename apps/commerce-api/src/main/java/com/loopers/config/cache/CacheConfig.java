package com.loopers.config.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * 상품 조회 캐시(Redis) 설정. 사용자 무관 데이터만 캐싱하므로 좋아요 여부(liked)는 캐시에 담지 않는다
 * (Facade가 캐시 결과 위에 사용자별로 조합). 좋아요 수(likesCount)는 캐시에 포함되지만 이미 비동기 집계로
 * 결과적 일관성이라, 별도 무효화 없이 아래 TTL로 stale 을 흡수한다.
 *
 * <p><b>TTL</b>: 상세는 단건 키라 변경 시 정밀 무효화가 가능해 길게(5분), 목록은 페이지 키라 특정 상품이
 * 어느 페이지인지 역추적이 어려워 무효화를 보수적으로(전체 evict)하므로 짧게(1분) 잡아 stale 노출을 줄인다.
 *
 * <p><b>키</b>: {@code cacheName::key} (RedisCacheManager 기본). 예) {@code product:detail::42},
 * {@code product:list::3:LIKES_DESC:0:20}. value는 JSON(GenericJackson2Json, 타입정보 포함).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 상품 상세 캐시명 (key = productId). 변경 시 해당 키만 정밀 evict. */
    public static final String PRODUCT_DETAIL = "product:detail";
    /** 상품 목록 캐시명 (key = brandId:sort:page:size). 변경 시 allEntries evict. */
    public static final String PRODUCT_LIST = "product:list";

    private static final Duration DETAIL_TTL = Duration.ofMinutes(5);
    private static final Duration LIST_TTL = Duration.ofMinutes(1);

    @Primary
    @Bean
    public RedisCacheManager productCacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 생성자를 쓴다: 자체 ObjectMapper에 default typing(@class)을 켜 역직렬화 시 구체 타입을 복원한다.
        // 외부 ObjectMapper를 주입하면 @class 가 빠져 캐시값이 Map으로 풀려 ClassCastException 이 난다.
        // 캐시 DTO(CachedProductDetail/ListItem)는 원시+String 필드뿐이라 커스텀 mapper 설정이 필요 없다.
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                PRODUCT_DETAIL, base.entryTtl(DETAIL_TTL),
                PRODUCT_LIST, base.entryTtl(LIST_TTL)
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(LIST_TTL))
                .withInitialCacheConfigurations(perCache)
                .build();
    }
}
