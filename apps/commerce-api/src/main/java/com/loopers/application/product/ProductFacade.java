package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductQueryService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.stock.StockService;
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
    private final StockService stockService;
    private final ProductQueryService productQueryService;

    /**
     * 상품 등록 — 활성 brand 검증 + 상품 생성 + 재고 초기화를 한 트랜잭션으로 묶는다(교차-Aggregate 원자 처리).
     * 재고는 독립 Aggregate(StockModel)라 상품 저장 후 productId로 초기화한다.
     */
    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, String imageUrl, Long price, Integer stock) {
        brandService.getActiveBrand(brandId);
        ProductModel product = productService.createProduct(brandId, name, description, imageUrl, price);
        stockService.initialize(product.getId(), stock);
        return ProductInfo.of(product, stock);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.of(product, stockService.getQuantity(id));
    }

    /**
     * 상품 상세 (UC-04) — Product+Brand+좋아요+재고 협력 조합은 도메인 서비스(ProductQueryService)에 위임하고,
     * Facade는 도메인 결과(ProductDetail)를 응답 DTO로 변환만 한다.
     */
    public ProductDetailInfo getProductDetail(Long id, Long userId) {
        ProductDetail detail = productQueryService.getProductDetail(id, userId);
        return ProductDetailInfo.of(detail.product(), detail.brand(), detail.liked(), detail.stockQuantity());
    }

    /**
     * 상품 목록 (UC-03) — 정렬·페이지·브랜드 필터 + 브랜드명·좋아요 여부·재고 협력 조합도 도메인 서비스에 위임.
     * Facade는 도메인 결과(ProductListEntry)를 응답 DTO로 변환만 한다.
     */
    public List<ProductListItemInfo> getProducts(Long brandId, ProductSortType sort, int page, int size, Long userId) {
        return productQueryService.getProductList(brandId, sort, page, size, userId).stream()
            .map(e -> ProductListItemInfo.of(e.product(), e.brandName(), e.liked(), e.stock()))
            .toList();
    }

    /**
     * 상품 수정 (UC-11 Admin) — 상품 속성 갱신 + 재고 절대값 조정을 한 트랜잭션으로 묶는다.
     */
    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, String imageUrl, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, imageUrl, price);
        stockService.adjust(id, stock);
        return ProductInfo.of(product, stock);
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
