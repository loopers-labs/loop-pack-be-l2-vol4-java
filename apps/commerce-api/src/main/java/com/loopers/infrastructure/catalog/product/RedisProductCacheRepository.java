package com.loopers.infrastructure.catalog.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.catalog.product.ProductCacheRepository;
import com.loopers.application.catalog.product.ProductResult;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
@EnableConfigurationProperties(ProductCacheProperties.class)
public class RedisProductCacheRepository implements ProductCacheRepository {
    private static final String DETAIL_PREFIX = "commerce:product:detail:v1:";
    private static final String LIST_PREFIX = "commerce:product:list:v1:";
    private static final String LIST_KEYS = "commerce:product:list:v1:keys";
    private static final TypeReference<PageResult<ProductResult>> PRODUCT_PAGE_TYPE = new TypeReference<>() {};

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductCacheProperties properties;

    @Override
    public Optional<ProductResult> getDetail(Long productId) {
        return get(detailKey(productId), ProductResult.class);
    }

    @Override
    public void putDetail(Long productId, ProductResult product) {
        put(detailKey(productId), product);
    }

    @Override
    public Optional<PageResult<ProductResult>> getList(ProductSearchCondition condition) {
        return get(listKey(condition), PRODUCT_PAGE_TYPE);
    }

    @Override
    public void putList(ProductSearchCondition condition, PageResult<ProductResult> products) {
        String key = listKey(condition);
        put(key, products);
        if (!isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForSet().add(LIST_KEYS, key);
            redisTemplate.expire(LIST_KEYS, ttl().plus(ttl()));
        } catch (RuntimeException e) {
            log.debug("Failed to track product list cache key. key={}", key, e);
        }
    }

    @Override
    public void evictDetail(Long productId) {
        if (!isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(detailKey(productId));
        } catch (RuntimeException e) {
            log.debug("Failed to evict product detail cache. productId={}", productId, e);
        }
    }

    @Override
    public void evictLists() {
        if (!isEnabled()) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.opsForSet().members(LIST_KEYS);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(LIST_KEYS);
        } catch (RuntimeException e) {
            log.debug("Failed to evict product list caches.", e);
        }
    }

    private <T> Optional<T> get(String key, Class<T> type) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, type));
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Failed to read product cache. key={}", key, e);
            return Optional.empty();
        }
    }

    private <T> Optional<T> get(String key, TypeReference<T> type) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, type));
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Failed to read product cache. key={}", key, e);
            return Optional.empty();
        }
    }

    private void put(String key, Object value) {
        if (!isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl());
        } catch (RuntimeException | JsonProcessingException e) {
            log.debug("Failed to write product cache. key={}", key, e);
        }
    }

    private boolean isEnabled() {
        return properties.isEnabled();
    }

    private Duration ttl() {
        Duration ttl = properties.getTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(5) : ttl;
    }

    private String detailKey(Long productId) {
        return DETAIL_PREFIX + productId;
    }

    private String listKey(ProductSearchCondition condition) {
        String brandId = condition.brandId() == null ? "all" : condition.brandId().toString();
        String status = condition.status() == null ? "all" : condition.status().name();
        return LIST_PREFIX
            + "status:" + status
            + ":brand:" + brandId
            + ":sort:" + condition.sort().name()
            + ":page:" + condition.page()
            + ":size:" + condition.size();
    }
}
