package com.loopers.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class ProductCacheService {

    private static final String META_KEY        = "product:%d:meta";
    private static final String PRICE_KEY       = "product:%d:price";
    private static final String STOCK_KEY       = "product:%d:stock";
    private static final String LIKE_COUNT_KEY  = "product:%d:like_count";
    private static final String BRAND_KEY       = "brand:%d:info";
    private static final String LIST_KEY        = "product:list:%s:%d:%d:%s";
    private static final String LIST_KEY_PATTERN = "product:list:*";

    private static final Duration META_TTL        = Duration.ofMinutes(30);
    private static final Duration PRICE_TTL       = Duration.ofMinutes(10);
    private static final Duration STOCK_TTL       = Duration.ofMinutes(3);
    private static final Duration LIKE_COUNT_TTL  = Duration.ofMinutes(2);
    private static final Duration BRAND_TTL       = Duration.ofHours(1);
    private static final Duration LIST_TTL        = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheService(
        RedisTemplate<String, String> defaultRedisTemplate,
        @Qualifier("masterRedisTemplate") RedisTemplate<String, String> masterRedisTemplate,
        ObjectMapper objectMapper
    ) {
        this.readTemplate = defaultRedisTemplate;
        this.writeTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
    }

    private record CachedProductMeta(Long brandId, String name, String description) {
        static CachedProductMeta from(ProductModel m) {
            return new CachedProductMeta(m.getBrandId(), m.getName(), m.getDescription());
        }
    }

    private record CachedBrand(Long id, String name, String description) {
        static CachedBrand from(BrandModel m) {
            return new CachedBrand(m.getId(), m.getName(), m.getDescription());
        }

        BrandModel toModel() {
            return new BrandModel(id, name, description, null, null);
        }
    }

    // ── Product (meta + price + stock 묶음) ─────────────────────────────────

    public Optional<ProductModel> getProduct(Long id) {
        Optional<CachedProductMeta> meta = readJson(String.format(META_KEY, id), CachedProductMeta.class);
        Optional<Long> price = readString(String.format(PRICE_KEY, id)).map(Long::parseLong);
        Optional<Integer> stock = readString(String.format(STOCK_KEY, id)).map(Integer::parseInt);

        if (meta.isEmpty() || price.isEmpty() || stock.isEmpty()) return Optional.empty();

        CachedProductMeta m = meta.get();
        return Optional.of(new ProductModel(id, m.brandId(), m.name(), m.description(), price.get(), stock.get(), 0L, null, null));
    }

    public void putProduct(ProductModel product) {
        writeJson(String.format(META_KEY, product.getId()), CachedProductMeta.from(product), META_TTL);
        writeString(String.format(PRICE_KEY, product.getId()), String.valueOf(product.getPrice()), PRICE_TTL);
        writeString(String.format(STOCK_KEY, product.getId()), String.valueOf(product.getStock()), STOCK_TTL);
    }

    public void evictProduct(Long id) {
        delete(String.format(META_KEY, id));
        delete(String.format(PRICE_KEY, id));
        delete(String.format(STOCK_KEY, id));
    }

    // ── Stock 단독 무효화 (주문 발생 시) ────────────────────────────────────

    public void evictProductStock(Long id) {
        delete(String.format(STOCK_KEY, id));
    }

    // ── LikeCount (TTL 만료에만 의존, evict 없음) ───────────────────────────

    public Optional<Long> getProductLikeCount(Long id) {
        return readString(String.format(LIKE_COUNT_KEY, id)).map(Long::parseLong);
    }

    public void putProductLikeCount(Long id, Long likeCount) {
        writeString(String.format(LIKE_COUNT_KEY, id), String.valueOf(likeCount), LIKE_COUNT_TTL);
    }

    // ── Product List ─────────────────────────────────────────────────────────

    public Optional<List<ProductInfo>> getProductList(Long brandId, int page, int size, String sort) {
        String key = listKey(brandId, page, size, sort);
        try {
            String value = readTemplate.opsForValue().get(key);
            if (value == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(value, new TypeReference<>() {}));
        } catch (Exception e) {
            log.warn("Cache read failed: key={}", key, e);
            return Optional.empty();
        }
    }

    public void putProductList(Long brandId, int page, int size, String sort, List<ProductInfo> list) {
        writeJson(listKey(brandId, page, size, sort), list, LIST_TTL);
    }

    public void evictAllProductLists() {
        try {
            Set<String> keys = writeTemplate.keys(LIST_KEY_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                writeTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Cache evict failed: pattern={}", LIST_KEY_PATTERN, e);
        }
    }

    private String listKey(Long brandId, int page, int size, String sort) {
        String brand = brandId != null ? String.valueOf(brandId) : "all";
        String sortKey = sort != null ? sort : "default";
        return String.format(LIST_KEY, brand, page, size, sortKey);
    }

    // ── Brand ────────────────────────────────────────────────────────────────

    public Optional<BrandModel> getBrand(Long id) {
        return readJson(String.format(BRAND_KEY, id), CachedBrand.class).map(CachedBrand::toModel);
    }

    public void putBrand(BrandModel brand) {
        writeJson(String.format(BRAND_KEY, brand.getId()), CachedBrand.from(brand), BRAND_TTL);
    }

    public void evictBrand(Long id) {
        delete(String.format(BRAND_KEY, id));
    }

    // ── 내부 헬퍼 ───────────────────────────────────────────────────────────

    private Optional<String> readString(String key) {
        try {
            return Optional.ofNullable(readTemplate.opsForValue().get(key));
        } catch (Exception e) {
            log.warn("Cache read failed: key={}", key, e);
            return Optional.empty();
        }
    }

    private <T> Optional<T> readJson(String key, Class<T> type) {
        try {
            String value = readTemplate.opsForValue().get(key);
            if (value == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(value, type));
        } catch (Exception e) {
            log.warn("Cache read failed: key={}", key, e);
            return Optional.empty();
        }
    }

    private void writeString(String key, String value, Duration ttl) {
        try {
            writeTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Cache write failed: key={}", key, e);
        }
    }

    private <T> void writeJson(String key, T value, Duration ttl) {
        try {
            writeTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Cache write failed: key={}", key, e);
        }
    }

    private void delete(String key) {
        try {
            writeTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Cache evict failed: key={}", key, e);
        }
    }
}
