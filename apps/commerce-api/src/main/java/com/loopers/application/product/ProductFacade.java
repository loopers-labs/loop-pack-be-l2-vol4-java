package com.loopers.application.product;

import com.loopers.config.cache.CacheConfig;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductDisplayService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final ProductDisplayService productDisplayService;
    private final BrandService brandService;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    public ProductInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        if (!brandService.existsBrand(brandId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[brandId = " + brandId + "] 등록된 브랜드가 아닙니다.");
        }
        Product product = productService.createProduct(name, description, Money.of(price), stock, brandId);
        return ProductInfo.from(product);
    }

    /**
     * 상품 상세 조회. Spring Cache(@Cacheable) 로 단순 단건 key-value 캐싱.
     *  - cache name: productDetail (TTL 10분)
     *  - key: productId
     *  - 캐시 미스 또는 Redis 장애 시 자동으로 DB 조회로 fallback 됨 (Spring Cache 기본 동작)
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_PRODUCT_DETAIL, key = "#id", sync = true)
    public ProductDetailInfo getProductDetail(Long id) {
        log.debug("[Cache MISS] productDetail id={}", id);
        ProductDetail detail = productDisplayService.getProductDetail(id);
        return ProductDetailInfo.from(detail);
    }

    /**
     * 상품 목록 조회. RedisTemplate 으로 복합 키 + 조건부 캐싱을 명시적으로 제어.
     *  - 키 구조: productList::{brandId}:{sort}:{page}:{size}
     *  - TTL 1분 (좋아요 정렬 변동성 보호)
     *  - Redis 장애 시 try-catch 로 흡수 후 DB 조회 fallback (캐시 미스로 처리)
     */
    public List<ProductDetailInfo> getProducts(Long brandId, String sort, int page, int size) {
        // sort 를 먼저 정규화한 뒤 캐시 키를 만든다.
        // 그렇지 않으면 "LIKES_DESC" / "likes_desc" / "Likes_Desc" 가 모두 다른 키로 저장되어
        // 동일 조회가 캐시 분리되고 히트율이 떨어진다.
        ProductSortType sortType = ProductSortType.from(sort);
        String cacheKey = buildProductListKey(brandId, sortType, page, size);

        // 1) Redis 조회 (장애나도 서비스 안 죽게 try-catch)
        try {
            @SuppressWarnings("unchecked")
            List<ProductDetailInfo> cached = (List<ProductDetailInfo>) objectRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("[Cache HIT] {}", cacheKey);
                return cached;
            }
        } catch (Exception e) {
            log.warn("[Cache READ FAIL] key={} — DB 로 fallback: {}", cacheKey, e.getMessage());
        }

        // 2) DB 조회
        log.debug("[Cache MISS] {}", cacheKey);
        List<Product> products = productService.getProducts(brandId, sortType, page, size);
        List<ProductDetailInfo> result = productDisplayService.getProductDetails(products).stream()
            .map(ProductDetailInfo::from)
            .toList();

        // 3) 캐시 저장 (장애나도 응답엔 영향 없게 try-catch)
        try {
            objectRedisTemplate.opsForValue().set(cacheKey, result, CacheConfig.PRODUCT_LIST_TTL);
        } catch (Exception e) {
            log.warn("[Cache WRITE FAIL] key={}: {}", cacheKey, e.getMessage());
        }
        return result;
    }

    /**
     * 상품 정보 변경 시 해당 상품 상세 캐시 evict.
     * 목록 캐시는 키가 다양해 evict 불가 → TTL(1분) 자연 만료에 맡김.
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_PRODUCT_DETAIL, key = "#id")
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        Product product = productService.updateProduct(id, name, description, Money.of(price), stock);
        return ProductInfo.from(product);
    }

    @CacheEvict(cacheNames = CacheConfig.CACHE_PRODUCT_DETAIL, key = "#id")
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }

    private String buildProductListKey(Long brandId, ProductSortType sortType, int page, int size) {
        return CacheConfig.CACHE_PRODUCT_LIST_PREFIX
            + (brandId == null ? "all" : brandId) + ":"
            + sortType.name() + ":" + page + ":" + size;
    }
}
