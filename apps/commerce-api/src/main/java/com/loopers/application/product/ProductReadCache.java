package com.loopers.application.product;

import com.loopers.config.cache.CacheConfig;
import com.loopers.domain.product.ProductQueryService;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 상품 조회 캐시 어댑터 — 사용자 무관 상세/목록을 Redis에 캐싱하고, 상품 변경 시 무효화한다.
 *
 * <p>캐시 적용은 프록시 기반(AOP)이라 같은 빈 내부 호출은 동작하지 않으므로, 캐시 진입점을 별도 빈으로
 * 분리해 Facade가 외부 호출하게 한다. liked 조합은 캐시 밖(Facade)에서 한다 — 여기엔 사용자 무관 데이터만.
 *
 * <p>키 설계: 상세 {@code product:detail::{id}}, 목록 {@code product:list::{brandId}:{sort}:{page}:{size}}.
 * 무효화: 상세는 단건 키 정밀 evict, 목록은 페이지↔상품 역추적이 어려워 allEntries evict(보수적) + 짧은 TTL.
 */
@Component
@RequiredArgsConstructor
public class ProductReadCache {

    private final ProductQueryService productQueryService;

    @Cacheable(cacheNames = CacheConfig.PRODUCT_DETAIL, key = "#productId")
    public CachedProductDetail getDetail(Long productId) {
        return CachedProductDetail.from(productQueryService.getProductDetail(productId));
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCT_LIST,
            key = "#brandId + ':' + #sort + ':' + #page + ':' + #size")
    public List<CachedProductListItem> getList(Long brandId, ProductSortType sort, int page, int size) {
        return productQueryService.getProductList(brandId, sort, page, size).stream()
                .map(CachedProductListItem::from)
                .toList();
    }

    /** 상품 변경/삭제 시: 해당 상세 키 정밀 evict + 목록 전체 evict(어느 페이지인지 역추적 불가하므로). */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL, key = "#productId"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST, allEntries = true)
    })
    public void evictForProductChange(Long productId) {
    }

    /** 신규 등록 시: 상세는 아직 키가 없으니 목록만 evict(새 상품이 목록에 노출되도록). */
    @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST, allEntries = true)
    public void evictListForNewProduct() {
    }
}
