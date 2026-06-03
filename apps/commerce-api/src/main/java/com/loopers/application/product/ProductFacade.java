package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductQueryService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductQueryService productQueryService;

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
     * 상품 상세 (UC-04) — Product+Brand+좋아요 협력 조합은 도메인 서비스(ProductQueryService)에 위임하고,
     * Facade는 도메인 결과(ProductDetail)를 응답 DTO로 변환만 한다.
     */
    public ProductDetailInfo getProductDetail(Long id, Long userId) {
        ProductDetail detail = productQueryService.getProductDetail(id, userId);
        return ProductDetailInfo.of(detail.product(), detail.brand(), detail.liked());
    }

    /**
     * 상품 목록 (UC-03) — 정렬·페이지·브랜드 필터 + 브랜드명·좋아요 여부 협력 조합도 도메인 서비스에 위임.
     * Facade는 도메인 결과(ProductListEntry)를 응답 DTO로 변환만 한다.
     */
    public List<ProductListItemInfo> getProducts(Long brandId, ProductSortType sort, int page, int size, Long userId) {
        return productQueryService.getProductList(brandId, sort, page, size, userId).stream()
            .map(e -> ProductListItemInfo.of(e.product(), e.brandName(), e.liked()))
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
