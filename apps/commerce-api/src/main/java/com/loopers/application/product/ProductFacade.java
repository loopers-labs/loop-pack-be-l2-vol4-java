package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;

    public ProductInfo createProduct(Long brandId, String name, String description, String imageUrl, Long price, Integer stock) {
        // 활성 brand 존재 검증 (교차-Aggregate 조립은 Facade 책임 — 03 §4)
        brandService.getActiveBrand(brandId);
        ProductModel product = productService.createProduct(brandId, name, description, imageUrl, price, stock);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    /**
     * 상품 상세 — 활성 Product + 활성 Brand 조합 (UC-04). 둘 중 하나라도 비활성/부재면 NOT_FOUND.
     * userId가 있으면(식별된 User) 좋아요 여부를 함께 채운다. Guest(userId == null)는 liked=false.
     */
    public ProductDetailInfo getProductDetail(Long id, Long userId) {
        ProductModel product = productService.getActiveProduct(id);
        BrandModel brand = brandService.getActiveBrand(product.getBrandId());
        boolean liked = userId != null && likeService.isLiked(userId, id);
        return ProductDetailInfo.of(product, brand, liked);
    }

    /**
     * 상품 목록 (UC-03) — 정렬·페이지·브랜드 필터 + 식별된 User의 좋아요 여부.
     * 좋아요 여부는 한 번의 batch 조회(IN)로 채워 N+1을 피한다. Guest(userId == null)는 전부 false.
     */
    public List<ProductListItemInfo> getProducts(Long brandId, ProductSortType sort, int page, int size, Long userId) {
        List<ProductModel> products = productService.getProducts(brandId, sort, page, size);

        // 브랜드명: brandId들을 모아 한 번에 조회(batch) → N+1 회피
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, String> brandNames = brandService.findByIds(brandIds).stream()
            .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));

        // 좋아요 여부: 식별된 User만, 역시 batch
        Set<Long> likedIds = (userId == null)
            ? Set.of()
            : likeService.findLikedProductIds(userId, products.stream().map(ProductModel::getId).toList());

        return products.stream()
            .map(p -> ProductListItemInfo.of(p, brandNames.get(p.getBrandId()), likedIds.contains(p.getId())))
            .toList();
    }

    public ProductInfo updateProduct(Long id, String name, String description, String imageUrl, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, imageUrl, price, stock);
        return ProductInfo.from(product);
    }

    /**
     * 상품 삭제 — soft delete + 해당 상품의 좋아요 cascade 비활성 (01 §7.5 Product→Like 전파).
     * 두 Aggregate 변경을 한 트랜잭션으로 묶어 원자적으로 처리한다(한쪽 실패 시 전체 롤백).
     */
    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        likeService.deactivateByProduct(id);
    }
}
