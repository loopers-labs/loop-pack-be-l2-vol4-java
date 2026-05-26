package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.stock.StockModel;
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
    private final StockService stockService;
    private final LikeService likeService;
    private final ProductDetailService productDetailService;

    /**
     * 사용자 - 상품 상세.
     *
     * <p>단순 Entity 1건 조회가 아니라 Product + Brand + Stock + LikeCount를 어셈블한
     * 상세 화면용 결과를 반환한다는 의도를 메서드명에서 드러낸다.
     *
     * <p>Product + Brand 조합은 Domain Service({@link ProductDetailService})에 위임하고,
     * Facade는 도메인 서비스의 결과에 재고 정보와 좋아요 수를 더해 응답 DTO로 어셈블한다.
     */
    public ProductInfo getProductDetail(Long productId) {
        ProductWithBrand pwb = productDetailService.assemble(productId);
        StockModel stock = stockService.getStock(productId);
        long likeCount = likeService.countByProductId(productId);
        return ProductInfo.forUser(pwb, stock, likeCount);
    }

    /**
     * 사용자 - 상품 목록.
     *
     * <p>재고/좋아요 수는 IN 쿼리로 일괄 조회하여 N+1을 회피한다.
     * 다중 원천 데이터를 DTO 리스트로 묶는 책임은 {@link ProductInfo#assembleUserList} 에 위임한다.
     */
    public List<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        List<ProductModel> products = productService.getProducts(brandId, sort, page, size);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        return ProductInfo.assembleUserList(
            products,
            stockService.getStocksByProductIds(productIds),
            likeService.countByProductIdIn(productIds)
        );
    }

    /** 어드민 - 상품 등록 (브랜드 검증 + 초기 재고 생성) */
    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer initialStock) {
        brandService.getBrand(brandId);
        ProductModel product = productService.createProduct(brandId, name, description, price);
        StockModel stock = stockService.createStock(product.getId(), initialStock);
        return ProductInfo.forAdmin(product, stock);
    }

    /** 어드민 - 상품 수정 (브랜드 변경 불가, 재고 수량은 절대값 갱신) */
    @Transactional
    public ProductInfo updateProduct(Long productId, String name, String description, Long price, Integer stockQuantity) {
        ProductModel product = productService.updateProduct(productId, name, description, price);
        if (stockQuantity != null) {
            stockService.changeQuantity(productId, stockQuantity);
        }
        StockModel stock = stockService.getStock(productId);
        return ProductInfo.forAdmin(product, stock);
    }

    /** 어드민 - 상품 단일 조회 */
    public ProductInfo getProductForAdmin(Long productId) {
        ProductModel product = productService.getProduct(productId);
        StockModel stock = stockService.getStock(productId);
        return ProductInfo.forAdmin(product, stock);
    }

    /** 어드민 - 상품 목록 (재고 IN 쿼리로 N+1 회피, 어셈블은 DTO에 위임) */
    public List<ProductInfo> getProductsForAdmin(Long brandId, String sort, int page, int size) {
        List<ProductModel> products = productService.getProducts(brandId, sort, page, size);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        return ProductInfo.assembleAdminList(
            products,
            stockService.getStocksByProductIds(productIds)
        );
    }

    public void deleteProduct(Long productId) {
        productService.deleteProduct(productId);
    }
}
