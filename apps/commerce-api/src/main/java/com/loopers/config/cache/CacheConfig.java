package com.loopers.config.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * 5주차 Redis 캐시 설정.
 *
 *  - {@link RedisCacheManager}: @Cacheable 용. cacheName 별로 TTL 분리.
 *  - {@link RedisTemplate}<String, Object>: 명시적 캐시 흐름 제어용 (상품 목록 등).
 *
 * 직렬화: Jackson JSON (record 도 OK). type-info 를 함께 저장해 List/제네릭 역직렬화 가능.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 상품 상세 캐시 이름 — @Cacheable(cacheNames = ...) 에서 참조 */
    public static final String CACHE_PRODUCT_DETAIL = "productDetail";

    /** 상품 목록 캐시 키 prefix — RedisTemplate 직접 사용 시 */
    public static final String CACHE_PRODUCT_LIST_PREFIX = "productList::";

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration PRODUCT_DETAIL_TTL = Duration.ofMinutes(10);
    public static final Duration PRODUCT_LIST_TTL = Duration.ofMinutes(1);

    /**
     * @Cacheable 용 RedisCacheManager.
     * - 기본 TTL 5분, productDetail 만 10분
     * - null 캐싱 비활성화 (캐시 미스로 처리)
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(DEFAULT_TTL)
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
            CACHE_PRODUCT_DETAIL, defaultConfig.entryTtl(PRODUCT_DETAIL_TTL)
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(perCache)
            .build();
    }

    /**
     * 객체 직접 캐싱용 RedisTemplate. 기존 String/String RedisTemplate 과 분리한다.
     * 상품 목록처럼 복합 키 + 조건부 캐싱이 필요한 곳에서 사용.
     */
    @Bean
    public RedisTemplate<String, Object> objectRedisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer());
        return template;
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        // activateDefaultTyping: 직렬화 JSON 에 @class 타입 정보를 함께 저장 →
        //   List<ProductDetailInfo> 같은 제네릭/다형 객체도 역직렬화 시 정확한 타입 복원 가능.
        // 학습용이라 com.loopers.* 와 표준 컬렉션만 허용 (보안: 임의 클래스 역직렬화 차단).
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType("com.loopers.")
                    .allowIfSubType("java.util.")
                    .allowIfSubType("java.lang.")
                    .allowIfSubType("java.time.")
                    .allowIfSubType("java.math.")
                    .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
            );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
