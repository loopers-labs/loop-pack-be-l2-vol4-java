package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCacheRepository;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Repository
public class ProductCacheRepositoryImpl implements ProductCacheRepository {

    private static final String DETAIL_KEY_PREFIX = "product:cache:detail:";
    private static final String LIST_KEY_PREFIX = "product:cache:list:";
    private static final String LIST_KEY_PATTERN = "product:cache:list:*";
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration LIST_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Product> findById(Long productId) {
        try {
            String json = redisTemplate.opsForValue().get(DETAIL_KEY_PREFIX + productId);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductCacheDto.class).toDomain());
        } catch (Exception e) {
            log.warn("상품 상세 캐시 조회 실패, productId={}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(Product product) {
        try {
            String json = objectMapper.writeValueAsString(ProductCacheDto.from(product));
            redisTemplate.opsForValue().set(DETAIL_KEY_PREFIX + product.getId(), json, DETAIL_TTL);
        } catch (Exception e) {
            log.warn("상품 상세 캐시 저장 실패, productId={}", product.getId(), e);
        }
    }

    @Override
    public Optional<Page<Product>> findAll(Long brandId, ProductSort sort, int page, int size) {
        String key = listKey(brandId, sort, page, size);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductPageCacheDto.class).toDomain());
        } catch (Exception e) {
            log.warn("상품 목록 캐시 조회 실패, key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveAll(Long brandId, ProductSort sort, int page, int size, Page<Product> products) {
        try {
            String key = listKey(brandId, sort, page, size);
            String json = objectMapper.writeValueAsString(ProductPageCacheDto.from(products));
            redisTemplate.opsForValue().set(key, json, LIST_TTL);
        } catch (Exception e) {
            log.warn("상품 목록 캐시 저장 실패", e);
        }
    }

    @Override
    public void evict(Long productId) {
        try {
            redisTemplate.delete(DETAIL_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("상품 상세 캐시 삭제 실패, productId={}", productId, e);
        }
    }

    @Override
    public void evictAll() {
        try {
            Set<String> keys = redisTemplate.keys(LIST_KEY_PATTERN);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("상품 목록 캐시 전체 삭제 실패", e);
        }
    }

    private String listKey(Long brandId, ProductSort sort, int page, int size) {
        String brandIdStr = brandId == null ? "all" : String.valueOf(brandId);
        return LIST_KEY_PREFIX + sort.name() + ":" + brandIdStr + ":" + page + ":" + size;
    }
}