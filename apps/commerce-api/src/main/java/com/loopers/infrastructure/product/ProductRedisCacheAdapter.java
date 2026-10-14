package com.loopers.infrastructure.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductCachePort;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSortType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 상품 조회 캐시 — Redis Look-aside 어댑터.
 * <p>
 * 키 전략:
 * - 상세: "product:detail:{productId}"
 * - 목록: "product:list:{brandId|all}:{sortType}"
 * <p>
 * TTL:
 * - 상세 5분 — 좋아요/재고 변경 빈도와 절충
 * - 목록 1분 — 다수 사용자가 공유하는 페이지, stale tolerance 낮음
 * <p>
 * 무효화 전략 (write-around 보강):
 * - 좋아요 변경 / 재고 차감 → evictDetail(productId)
 * - 상품 생성/수정 → evictListsByBrand(brandId)  (정렬 조합 전체 일괄)
 * <p>
 * 장애 격리: Redis 예외는 삼키고 빈 결과를 반환한다 — DB fallback 으로 정상 동작 보장.
 */
@Component
public class ProductRedisCacheAdapter implements ProductCachePort {

    private static final String DETAIL_KEY_PREFIX = "product:detail:";
    private static final String LIST_KEY_PREFIX = "product:list:";
    private static final Duration DETAIL_TTL = Duration.ofMinutes(5);
    private static final Duration LIST_TTL = Duration.ofMinutes(1);
    private static final TypeReference<List<ProductInfo>> LIST_TYPE = new TypeReference<>() {};

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductRedisCacheAdapter(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProductDetailInfo> getDetail(Long productId) {
        try {
            String raw = redisTemplate.opsForValue().get(detailKey(productId));
            if (raw == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(raw, ProductDetailInfo.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void putDetail(ProductDetailInfo info) {
        try {
            String raw = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(detailKey(info.productId()), raw, DETAIL_TTL);
        } catch (Exception e) {
            // silently ignore — 캐시 미스로 동작
        }
    }

    @Override
    public void evictDetail(Long productId) {
        try {
            redisTemplate.delete(detailKey(productId));
        } catch (Exception e) {
            // silently ignore
        }
    }

    @Override
    public Optional<List<ProductInfo>> getList(Long brandId, ProductSortType sortType) {
        try {
            String raw = redisTemplate.opsForValue().get(listKey(brandId, sortType));
            if (raw == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(raw, LIST_TYPE));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void putList(Long brandId, ProductSortType sortType, List<ProductInfo> infos) {
        try {
            String raw = objectMapper.writeValueAsString(infos);
            redisTemplate.opsForValue().set(listKey(brandId, sortType), raw, LIST_TTL);
        } catch (Exception e) {
            // silently ignore
        }
    }

    @Override
    public void evictListsByBrand(Long brandId) {
        try {
            List<String> keysToDelete = scanListKeys(brandId);
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception e) {
            // silently ignore
        }
    }

    private List<String> scanListKeys(Long brandId) {
        String pattern = listKeyBrandPrefix(brandId) + "*";
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
        return keys;
    }

    private static String detailKey(Long productId) {
        return DETAIL_KEY_PREFIX + productId;
    }

    private static String listKey(Long brandId, ProductSortType sortType) {
        return listKeyBrandPrefix(brandId) + sortType.name();
    }

    private static String listKeyBrandPrefix(Long brandId) {
        return LIST_KEY_PREFIX + (brandId == null ? "all" : brandId) + ":";
    }
}
