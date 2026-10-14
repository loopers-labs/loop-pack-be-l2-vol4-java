package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.event.ProductViewedEvent;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductDetailService.ProductDetail;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 상품 조회는 Look-aside 캐시(Redis) 를 거친다.
 * <p>
 * 정책:
 * - 상세/목록 GET: 캐시 HIT 시 즉시 반환, MISS 시 DB → 캐시 적재
 * - 상품 생성: 동일 브랜드의 목록 캐시 무효화 (정렬 조합 + 전체("all") 스코프)
 * <p>
 * 좋아요/재고 변경에 따른 상세 캐시 무효화는 각 Facade(LikeFacade, OrderFacade) 가 직접 책임진다.
 */
@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductDetailService productDetailService;
    private final ProductCachePort productCache;
    private final ApplicationEventPublisher eventPublisher;

    public ProductInfo createProduct(ProductCriteria.Create criteria) {
        brandService.requireExists(criteria.brandId());
        ProductModel product = productService.createProduct(
            criteria.brandId(),
            criteria.name(),
            criteria.description(),
            criteria.price(),
            criteria.stock(),
            criteria.imageUrl()
        );
        // 새 상품 등장 → 해당 브랜드 + 전체 목록 캐시 무효화 (다음 조회에서 갱신)
        productCache.evictListsByBrand(criteria.brandId());
        productCache.evictListsByBrand(null);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    /**
     * 상품 상세: 캐시 우선 조회 → 미스 시 도메인 조합 → 캐시 적재.
     * 조회 자체를 유저 행동 이벤트(ProductViewedEvent) 로 발행 — Consumer 가 조회수 집계.
     * userId 가 없어도 (익명 조회) 이벤트는 발행한다.
     */
    @Transactional
    public ProductDetailInfo getProductDetail(Long id, Long userId) {
        Optional<ProductDetailInfo> cached = productCache.getDetail(id);
        ProductDetailInfo info = cached.orElseGet(() -> {
            ProductDetail detail = productDetailService.getDetail(id);
            ProductDetailInfo built = ProductDetailInfo.from(detail);
            productCache.putDetail(built);
            return built;
        });
        eventPublisher.publishEvent(ProductViewedEvent.of(userId, id));
        return info;
    }

    public ProductDetailInfo getProductDetail(Long id) {
        return getProductDetail(id, null);
    }

    public List<ProductInfo> listProducts(ProductCriteria.List criteria) {
        ProductSortType effectiveSort = criteria.sortType() == null ? ProductSortType.LATEST : criteria.sortType();
        Optional<List<ProductInfo>> cached = productCache.getList(criteria.brandId(), effectiveSort);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<ProductModel> products = productService.listProducts(effectiveSort, criteria.brandId());
        List<ProductInfo> infos = products.stream()
            .map(ProductInfo::from)
            .toList();
        productCache.putList(criteria.brandId(), effectiveSort, infos);
        return infos;
    }
}
