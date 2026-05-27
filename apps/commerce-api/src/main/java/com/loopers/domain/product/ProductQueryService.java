package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 상품 상세 — 활성 Product + 활성 Brand 조합. 둘 중 하나라도 비활성/부재면 NOT_FOUND (UC-04). */
    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(Long productId, Long userId) {
        ProductModel product = productService.getActiveProduct(productId);
        BrandModel brand = brandService.getActiveBrand(product.getBrandId());
        boolean liked = userId != null && likeService.isLiked(userId, productId);
        return new ProductDetail(product, brand, liked);
    }

    /**
     * 상품 목록 — 정렬·페이지·브랜드 필터 위에 브랜드명·좋아요 여부를 조합한다 (UC-03).
     * 브랜드명과 좋아요 여부는 각각 한 번의 batch 조회(IN)로 채워 N+1을 피한다.
     */
    @Transactional(readOnly = true)
    public List<ProductListEntry> getProductList(Long brandId, ProductSortType sort, int page, int size, Long userId) {
        List<ProductModel> products = productService.getProducts(brandId, sort, page, size);

        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, String> brandNames = brandService.findByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));

        Set<Long> likedIds = (userId == null)
                ? Set.of()
                : likeService.findLikedProductIds(userId, products.stream().map(ProductModel::getId).toList());

        return products.stream()
                .map(p -> new ProductListEntry(p, brandNames.get(p.getBrandId()), likedIds.contains(p.getId())))
                .toList();
    }
}
