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
    private final BrandService brandService;
    private final LikeService likeService;
    private final StockService stockService;

    /**
     * 상품 상세 — 활성 Product + 활성 Brand + 재고 수량 조합 (<b>사용자 무관</b>, 캐시 가능).
     * Product/Brand 중 하나라도 비활성/부재면 NOT_FOUND (UC-04). 좋아요 여부는 Facade가 별도 조합한다.
     */
    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(Long productId) {
        ProductModel product = productService.getActiveProduct(productId);
        BrandModel brand = brandService.getActiveBrand(product.getBrandId());
        int stockQuantity = stockService.getQuantity(productId);
        return new ProductDetail(product, brand, stockQuantity);
    }

    /**
     * 상품 목록 — 정렬·페이지·브랜드 필터 위에 브랜드명을 조합한다 (UC-03, <b>사용자 무관</b>, 캐시 가능).
     * 브랜드명은 한 번의 batch 조회(IN)로 채워 N+1을 피한다. 좋아요 여부는 Facade가 별도 batch 조합한다.
     */
    @Transactional(readOnly = true)
    public List<ProductListEntry> getProductList(Long brandId, ProductSortType sort, String cursor, int size) {
        List<ProductModel> products = productService.getProducts(brandId, sort, cursor, size);

        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, String> brandNames = brandService.findByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));

        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        Map<Long, Integer> stocks = stockService.findQuantities(productIds);

        return products.stream()
                .map(p -> new ProductListEntry(
                        p,
                        brandNames.get(p.getBrandId()),
                        stocks.getOrDefault(p.getId(), 0)))
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
