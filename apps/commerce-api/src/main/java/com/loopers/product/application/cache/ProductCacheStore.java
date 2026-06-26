package com.loopers.product.application.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.product.application.ProductCommand;
import com.loopers.product.application.ProductResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 상품 조회 결과를 Redis 에 명시적으로 캐싱한다(look-aside). 값은 JSON 문자열로 직렬화한다.
 * 상세는 productId, 목록은 첫 페이지(정렬+브랜드+size) 단위로 캐싱한다.
 */
@Slf4j
@Component
public class ProductCacheStore {

    private static final String DETAIL_KEY = "product:detail:v1:%d";
    private static final String FIRST_PAGE_KEY = "product:list:v1:%s:brand=%s:size=%d";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ProductCacheStore(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            @Value("${product.cache.ttl-seconds:30}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<ProductResult.Detail> getDetail(Long productId) {
        return read(detailKey(productId), ProductResult.Detail.class);
    }

    public void putDetail(Long productId, ProductResult.Detail detail) {
        write(detailKey(productId), detail);
    }

    public Optional<ProductResult.Page> getFirstPage(ProductCommand.PageQuery query) {
        return read(firstPageKey(query), ProductResult.Page.class);
    }

    public void putFirstPage(ProductCommand.PageQuery query, ProductResult.Page page) {
        write(firstPageKey(query), page);
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("상품 캐시 조회 실패 key={} ({}). 캐시 미스로 처리한다.", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("상품 캐시 저장 실패 key={} ({}). 캐싱 생략한다.", key, e.getMessage());
        }
    }

    private String detailKey(Long productId) {
        return DETAIL_KEY.formatted(productId);
    }

    private String firstPageKey(ProductCommand.PageQuery query) {
        String brand = query.brandId() == null ? "all" : String.valueOf(query.brandId());
        return FIRST_PAGE_KEY.formatted(query.sort(), brand, query.size());
    }
}
