package com.loopers.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductCachePort;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.domain.productrank.RankedProduct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 상품 캐시 어댑터. domain/Facade 가 Redis 를 모르게 격리한다(seam).
 * 미스/Redis 장애 시 빈 결과를 반환해 호출부가 DB 로 폴백하도록 한다("있으면 빠르고 없어도 맞게").
 */
@RequiredArgsConstructor
@Component
public class ProductCacheStore implements ProductCachePort {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheStore.class);

    private final RedisTemplate<String, String> redisTemplate; // RedisConfig 의 @Primary
    private final ObjectMapper objectMapper;

    private String detailKey(Long id) {
        return "product:detail:" + id;
    }

    @Override
    public Optional<ProductDetailInfo> getDetail(Long id) {
        try {
            String json = redisTemplate.opsForValue().get(detailKey(id));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductDetailInfo.class));
        } catch (Exception e) {
            log.warn("[cache] getDetail fallback id={} : {}", id, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void putDetail(ProductDetailInfo info, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(detailKey(info.id()), objectMapper.writeValueAsString(info), ttl);
        } catch (Exception e) {
            log.warn("[cache] putDetail skip id={} : {}", info.id(), e.toString());
        }
    }

    @Override
    public void evictDetail(Long id) {
        try {
            redisTemplate.delete(detailKey(id));
        } catch (Exception e) {
            log.warn("[cache] evictDetail skip id={} : {}", id, e.toString());
        }
    }

    private String listKey(Long brandId) {
        return "list:" + (brandId == null ? "all" : brandId) + ":likes";
    }

    @Override
    public Optional<List<RankedProduct>> getLikesBlob(Long brandId) {
        try {
            String json = redisTemplate.opsForValue().get(listKey(brandId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, new TypeReference<List<RankedProduct>>() {}));
        } catch (Exception e) {
            log.warn("[cache] getLikesBlob fallback brand={} : {}", brandId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void putLikesBlob(Long brandId, List<RankedProduct> ranked, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(listKey(brandId), objectMapper.writeValueAsString(ranked), ttl);
        } catch (Exception e) {
            log.warn("[cache] putLikesBlob skip brand={} : {}", brandId, e.toString());
        }
    }
}
