package com.loopers.application.product;

import com.loopers.application.activity.ProductListViewedEvent;
import com.loopers.application.activity.ProductViewedEvent;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.infrastructure.product.ProductCacheStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 상품 유스케이스 Application Service.
 *
 * <p>스타일 2: Application Layer 가 Repository 를 직접 보유하여 조회/저장을 책임진다.
 * Product 도메인 자체는 CRUD 위주라 별도 Domain Service 가 없고,
 * Product+Brand 조합만 {@link ProductDetailService}(순수 Domain Service)에 위임한다.
 */
@RequiredArgsConstructor
@Service
public class ProductApplicationService {

    private static final Set<String> VALID_SORT_VALUES = Set.of("latest", "price_asc", "likes_desc");

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final StockRepository stockRepository;
    private final ProductDetailService productDetailService;
    private final ProductCacheStore productCacheStore;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 사용자 - 상품 상세 (Product + Brand + Stock + LikeCount 어셈블).
     *
     * <p>Redis 캐시 read-through: 히트 시 상품 기본 정보는 캐시에서 반환하고,
     * 재고(inStock/remainingStock)는 항상 DB 에서 실시간 조회한다.
     * 주문 처리 중 재고가 변동되어도 캐시된 값이 노출되지 않도록 분리한다.
     * 캐시 장애 시에는 미스로 처리되어 DB 로 폴백한다(ProductCacheStore 내부 격리).
     */
    @Transactional(readOnly = true)
    public ProductInfo getProductDetail(Long productId) {
        // 재고는 캐시 분리 정책 — 캐시 히트 여부와 무관하게 항상 DB 직접 조회.
        // 주문으로 재고가 실시간 변동되므로 캐시 값을 신뢰할 수 없다.
        StockModel stock = findStockOrThrow(productId);
        // 조회 행동 로깅 — 캐시 히트/미스와 무관하게 발행 (비로그인 조회라 userId 는 null)
        eventPublisher.publishEvent(new ProductViewedEvent(null, productId));
        Optional<ProductInfo> cached = productCacheStore.getDetail(productId);
        if (cached.isPresent()) {
            return cached.get().withStock(stock);
        }
        ProductModel product = findActiveProductOrThrow(productId);
        BrandModel brand = findBrandOrThrow(product.getBrandId());
        ProductWithBrand pwb = productDetailService.assemble(product, brand);
        ProductInfo info = ProductInfo.forUser(pwb, stock);   // 좋아요 수는 비정규화 컬럼(like_count) 사용
        productCacheStore.putDetail(productId, info);
        return info;
    }

    /**
     * 사용자 - 상품 목록.
     *
     * <p>Redis 캐시 read-through. 빈 결과는 캐시하지 않는다(상품 추가 시 무효화 대상에서 누락 방지 + 의미 없음).
     * 재고는 IN 쿼리로 일괄 조회하여 N+1을 회피하고, 좋아요 수는 비정규화 컬럼을 사용한다.
     */
    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        validateSort(sort);
        // 목록 페이지 조회 행동 로깅 — 요청당 1건(페이지 단위), 캐시 히트/미스와 무관하게 발행
        eventPublisher.publishEvent(new ProductListViewedEvent(brandId, sort, page));
        Optional<List<ProductInfo>> cached = productCacheStore.getList(brandId, sort, page, size);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<ProductModel> products = productRepository.findAll(brandId, sort, page, size);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        List<ProductInfo> infos = ProductInfo.assembleUserList(
            products,
            stockRepository.findAllByProductIdIn(productIds)
        );
        productCacheStore.putList(brandId, sort, page, size, infos);
        return infos;
    }

    /** 어드민 - 상품 등록 (브랜드 검증 + 초기 재고 생성) */
    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer initialStock) {
        findBrandOrThrow(brandId);   // 존재 검증만
        ProductModel product = productRepository.save(new ProductModel(brandId, name, description, price));
        StockModel stock = stockRepository.save(StockModel.of(product.getId(), initialStock));
        // 신규 상품은 목록 캐시를 즉시 무효화하지 않는다 — TTL 만료 시 자동 반영 (목록 캐시 TTL 3분)
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
        // 가격/이름/재고 변경 → 상세 + 목록 캐시 무효화 (커밋 후)
        eventPublisher.publishEvent(ProductCacheEvictEvent.detailAndList(productId));
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
        // 삭제된 상품이 상세/목록에서 즉시 사라지도록 무효화 (커밋 후)
        eventPublisher.publishEvent(ProductCacheEvictEvent.detailAndList(productId));
    }

    /**
     * 어드민 - like_count 재집계 (drift 보정).
     *
     * <p>실시간 원자 동기화로 누적될 수 있는 오차를 실제 likes 집계로 리셋한다.
     * 재집계로 좋아요 수가 바뀌므로 목록 캐시를 무효화한다(상세는 TTL 로 수렴).
     *
     * @return 갱신된 상품 수
     */
    @Transactional
    public int resyncLikeCounts() {
        int updated = productRepository.resyncAllLikeCounts();
        eventPublisher.publishEvent(ProductCacheEvictEvent.listOnly());
        return updated;
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
