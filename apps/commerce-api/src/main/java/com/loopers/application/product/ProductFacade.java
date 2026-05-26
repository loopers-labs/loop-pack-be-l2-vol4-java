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
     */
    public ProductDetailInfo getProductDetail(Long id) {
        ProductModel product = productService.getActiveProduct(id);
        BrandModel brand = brandService.getActiveBrand(product.getBrandId());
        return ProductDetailInfo.of(product, brand);
    }

    public List<ProductInfo> getProducts(Long brandId, ProductSortType sort, int page, int size) {
        return productService.getProducts(brandId, sort, page, size).stream()
            .map(ProductInfo::from)
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
