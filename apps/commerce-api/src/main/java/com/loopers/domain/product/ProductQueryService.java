package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 상품 조회 도메인 서비스 — 여러 도메인(Product/Brand/Like)에 걸친 협력 조회를 담는다.
 * 상태를 갖지 않고(stateless) 도메인 서비스들의 협력만 조율하며, 도메인 객체를 반환한다.
 * 단건/목록 모두 활성 자원만 노출하며, 좋아요 여부는 식별된 User에게만 채운다(UC-03/04).
 */
@Component
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductService productService;
    private final ProductMetricsService productMetricsService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final StockService stockService;

    /**
     * 상품 상세 — 활성 Product + 활성 Brand + 재고 수량 + 좋아요 수 조합 (<b>사용자 무관</b>, 캐시 가능).
     * Product/Brand 중 하나라도 비활성/부재면 NOT_FOUND (UC-04). 좋아요 수는 read model(product_metrics)에서
     * 조합한다(비동기 집계·결과적 일관성). 좋아요 여부(liked)는 Facade가 별도 조합한다.
     */
    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(Long productId) {
        ProductModel product = productService.getActiveProduct(productId);
        BrandModel brand = brandService.getActiveBrand(product.getBrandId());
        int stockQuantity = stockService.getQuantity(productId);
        long likeCount = productMetricsService.getLikeCount(productId);
        return new ProductDetail(product, brand, stockQuantity, likeCount);
    }

    /**
     * 상품 목록 — 정렬·페이지·브랜드 필터는 read model(product_metrics) 단일 테이블에서 처리하고(좋아요순 포함),
     * 그 id 순서 위에 표시 필드(product)·브랜드명·재고·좋아요 수를 batch(IN)로 조합해 N+1을 피한다
     * (UC-03, <b>사용자 무관</b>, 캐시 가능). 좋아요 여부는 Facade가 별도 batch 조합한다.
     */
    @Transactional(readOnly = true)
    public List<ProductListEntry> getProductList(Long brandId, ProductSortType sort, int page, int size) {
        List<Long> orderedIds = productMetricsService.getActiveProductIdsPage(brandId, sort, page, size);
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductModel> productById = productService.findActiveByIds(orderedIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        List<Long> foundBrandIds = productById.values().stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, String> brandNames = brandService.findByIds(foundBrandIds).stream()
                .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));
        Map<Long, Integer> stocks = stockService.findQuantities(orderedIds);
        Map<Long, Long> likeCounts = productMetricsService.getLikeCounts(orderedIds);

        // read model 이 준 정렬 순서를 유지하며 조합한다. 조합 시점에 비활성/부재가 된 상품은 조용히 제외한다.
        return orderedIds.stream()
                .map(productById::get)
                .filter(Objects::nonNull)
                .map(p -> new ProductListEntry(
                        p,
                        brandNames.get(p.getBrandId()),
                        stocks.getOrDefault(p.getId(), 0),
                        likeCounts.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    /**
     * 내가 좋아요한 상품 목록 (UC-07) — 좋아요 시점 최신순. 좋아요는 살아있어도 상품·브랜드가
     * 비활성된 경우 결과에서 제외하며, 상품 활성 검증은 batch(IN)로 한 번에 처리해 N+1을 피한다.
     */
    @Transactional(readOnly = true)
    public List<ProductModel> getMyLikedProducts(Long userId, int page, int size) {
        List<LikeModel> likes = likeService.getMyActiveLikes(userId, page, size);
        if (likes.isEmpty()) return List.of();

        List<Long> productIds = likes.stream().map(LikeModel::getProductId).toList();
        Map<Long, ProductModel> activeById = productService.findActiveByIds(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        return likes.stream()
                .map(l -> activeById.get(l.getProductId()))
                .filter(Objects::nonNull)
                .toList();
    }
}
