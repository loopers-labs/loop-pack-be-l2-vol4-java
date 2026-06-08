package com.loopers.application.product;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 상품 유스케이스 Facade.
 *
 * <p>스타일 2: Application Layer 가 Repository 를 직접 보유하여 조회/저장을 책임진다.
 * Product 도메인 자체는 CRUD 위주라 별도 Domain Service 가 없고,
 * Product+Brand 조합만 {@link ProductDetailService}(순수 Domain Service)에 위임한다.
 */
@RequiredArgsConstructor
@Component
public class ProductFacade {

    private static final Set<String> VALID_SORT_VALUES = Set.of("latest", "price_asc", "likes_desc");

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final StockRepository stockRepository;
    private final LikeFacade likeFacade;
    private final ProductDetailService productDetailService;

    /** 사용자 - 상품 상세 (Product + Brand + Stock + LikeCount 어셈블) */
    @Transactional(readOnly = true)
    public ProductInfo getProductDetail(Long productId) {
        ProductModel product = findActiveProductOrThrow(productId);
        BrandModel brand = findBrandOrThrow(product.getBrandId());
        ProductWithBrand pwb = productDetailService.assemble(product, brand);
        StockModel stock = findStockOrThrow(productId);
        long likeCount = likeFacade.countByProductId(productId);
        return ProductInfo.forUser(pwb, stock, likeCount);
    }

    /**
     * 사용자 - 상품 목록.
     *
     * <p>재고/좋아요 수는 IN 쿼리로 일괄 조회하여 N+1을 회피한다.
     * 다중 원천 데이터를 DTO 리스트로 묶는 책임은 {@link ProductInfo#assembleUserList} 에 위임한다.
     */
    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        validateSort(sort);
        List<ProductModel> products = productRepository.findAll(brandId, sort, page, size);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        return ProductInfo.assembleUserList(
            products,
            stockRepository.findAllByProductIdIn(productIds),
            likeFacade.countByProductIdIn(productIds)
        );
    }

    /** 어드민 - 상품 등록 (브랜드 검증 + 초기 재고 생성) */
    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer initialStock) {
        findBrandOrThrow(brandId);   // 존재 검증만
        ProductModel product = productRepository.save(new ProductModel(brandId, name, description, price));
        StockModel stock = stockRepository.save(StockModel.of(product.getId(), initialStock));
        return ProductInfo.forAdmin(product, stock);
    }

    /** 어드민 - 상품 수정 (브랜드 변경 불가, 재고 수량은 절대값 갱신) */
    @Transactional
    public ProductInfo updateProduct(Long productId, String name, String description, Long price, Integer stockQuantity) {
        ProductModel product = findActiveProductOrThrow(productId);
        product.update(name, description, price);
        productRepository.save(product);

        StockModel stock = findStockOrThrow(productId);
        if (stockQuantity != null) {
            stock.changeQuantity(stockQuantity);
            stockRepository.save(stock);
        }
        return ProductInfo.forAdmin(product, stock);
    }

    /** 어드민 - 상품 단일 조회 */
    @Transactional(readOnly = true)
    public ProductInfo getProductForAdmin(Long productId) {
        ProductModel product = findActiveProductOrThrow(productId);
        StockModel stock = findStockOrThrow(productId);
        return ProductInfo.forAdmin(product, stock);
    }

    /** 어드민 - 상품 목록 (재고 IN 쿼리로 N+1 회피, 어셈블은 DTO에 위임) */
    @Transactional(readOnly = true)
    public List<ProductInfo> getProductsForAdmin(Long brandId, String sort, int page, int size) {
        validateSort(sort);
        List<ProductModel> products = productRepository.findAll(brandId, sort, page, size);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        return ProductInfo.assembleAdminList(
            products,
            stockRepository.findAllByProductIdIn(productIds)
        );
    }

    @Transactional
    public void deleteProduct(Long productId) {
        ProductModel product = findActiveProductOrThrow(productId);
        product.delete();
        productRepository.save(product);
    }

    private void validateSort(String sort) {
        if (sort != null && !VALID_SORT_VALUES.contains(sort)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "유효하지 않은 정렬 옵션입니다. (허용값: latest, price_asc, likes_desc)");
        }
    }

    private ProductModel findActiveProductOrThrow(Long productId) {
        ProductModel product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        if (product.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND,
                "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
        return product;
    }

    private BrandModel findBrandOrThrow(Long brandId) {
        return brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));
    }

    private StockModel findStockOrThrow(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
    }
}
