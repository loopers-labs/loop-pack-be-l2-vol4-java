package com.loopers.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@EnableCaching
@Configuration
public class CacheConfig implements CachingConfigurer {

    public static final String PRODUCT_DETAIL = "product:detail";
    public static final String PRODUCT_LIST   = "product:list";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
            );
        om.addMixIn(PageImpl.class, PageImplMixin.class);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(serializer))
            .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(base)
            .withInitialCacheConfigurations(Map.of(
                PRODUCT_DETAIL, base.entryTtl(Duration.ofMinutes(5)),
                PRODUCT_LIST,   base.entryTtl(Duration.ofMinutes(1))
            ))
            .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("캐시 조회 실패 — cache={}, key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("캐시 저장 실패 — cache={}, key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("캐시 삭제 실패 — cache={}, key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("캐시 전체 삭제 실패 — cache={}: {}", cache.getName(), e.getMessage());
            }
        };
    }

    // PageImpl(List<T>, Pageable, long) 생성자를 Jackson이 사용하도록 안내
    @JsonIgnoreProperties(ignoreUnknown = true, value = "pageable")
    abstract static class PageImplMixin {
        @JsonCreator
        PageImplMixin(
            @JsonProperty("content")       List<?> content,
            @JsonProperty("pageable")      Pageable pageable,
            @JsonProperty("totalElements") long total
        ) {}
    }
}
