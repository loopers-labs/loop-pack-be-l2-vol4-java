package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductMetricsService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final ProductMetricsService productMetricsService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final StockService stockService;
    private final ProductReadCache productReadCache;
    private final ProductViewRecorder productViewRecorder;
    private final RankingRepository rankingRepository;

    /** 실시간 랭킹 일간 버킷 기준 시간대(KST). 상세의 "오늘" 순위 판단에 쓴다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 상품 등록 — 활성 brand 검증 + 상품 생성 + 재고 초기화를 한 트랜잭션으로 묶는다(교차-Aggregate 원자 처리).
     * 재고는 독립 Aggregate(StockModel)라 상품 저장 후 productId로 초기화한다.
     * 새 상품이 목록에 노출되도록 목록 캐시를 무효화한다(커밋 후).
     */
    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, String imageUrl, Long price, Integer stock) {
        brandService.getActiveBrand(brandId);
        ProductModel product = productService.createProduct(brandId, name, description, imageUrl, price);
        stockService.initialize(product.getId(), stock);
        productReadCache.evictListForNewProduct();
        return ProductInfo.of(product, stock, 0L); // 신규 상품 — 좋아요 0
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.of(product, stockService.getQuantity(id), productMetricsService.getLikeCount(id));
    }

    /**
     * 상품 상세 (UC-04) — 사용자 무관 조합(Product+Brand+재고)은 캐시(ProductReadCache)에서 가져오고,
     * 사용자별 좋아요 여부(liked)만 캐시 밖에서 실시간 조합한다. 식별된 User만 liked를 본다.
     */
    public ProductDetailInfo getProductDetail(Long id, Long userId) {
        CachedProductDetail base = productReadCache.getDetail(id); // 활성 상품만 통과(없으면 NOT_FOUND)
        productViewRecorder.record(id); // 조회수 집계 트리거(outbox → catalog-events PRODUCT_VIEWED)
        boolean liked = userId != null && likeService.isLiked(userId, id);
        // 오늘·어제 랭킹 순위(각각 없으면 null) — 캐시 밖에서 실시간 조합. 둘을 함께 줘 클라이언트가 추세를 판별한다
        // (상승/하락/신규진입/이탈은 두 순위의 조합으로만 나오고, 한쪽이 null 인 경우가 의미를 갖는다).
        // 어제 키는 TTL 2일 안이라 항상 유효하다. ZREVRANK 2회 — 상세 1건이라 파이프라이닝 없이 그대로 둔다.
        LocalDate today = LocalDate.now(KST);
        Long rank = rankingRepository.findRank(today, id).orElse(null);
        Long rankYesterday = rankingRepository.findRank(today.minusDays(1), id).orElse(null);
        return base.toInfo(liked, rank, rankYesterday);
    }

    /**
     * 상품 목록 (UC-03) — 정렬·페이지·브랜드 필터 + 브랜드명·재고 조합은 캐시에서 가져오고(사용자 무관),
     * 좋아요 여부만 식별된 User에 한해 한 번의 batch 조회로 조합한다(N+1 회피).
     */
    public List<ProductListItemInfo> getProducts(Long brandId, ProductSortType sort, int page, int size, Long userId) {
        List<CachedProductListItem> base = productReadCache.getList(brandId, sort, page, size);
        if (base.isEmpty()) {
            return List.of();
        }
        Set<Long> likedIds = (userId == null)
            ? Set.of()
            : likeService.findLikedProductIds(userId, base.stream().map(CachedProductListItem::id).toList());
        return base.stream()
            .map(item -> item.toInfo(likedIds.contains(item.id())))
            .toList();
    }

    /**
     * 상품 수정 (UC-11 Admin) — 상품 속성 갱신 + 재고 절대값 조정을 한 트랜잭션으로 묶고, 해당 상품
     * 상세 캐시 + 목록 캐시를 무효화한다.
     */
    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, String imageUrl, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, imageUrl, price);
        stockService.adjust(id, stock);
        productReadCache.evictForProductChange(id);
        return ProductInfo.of(product, stock, productMetricsService.getLikeCount(id));
    }

    /**
     * 상품 삭제 — soft delete + 해당 상품의 좋아요 cascade 비활성 (01 §7.5 Product→Like 전파).
     * 두 Aggregate 변경을 한 트랜잭션으로 묶어 원자적으로 처리하고, 상세·목록 캐시를 무효화한다.
     */
    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        likeService.deactivateByProduct(id);
        productReadCache.evictForProductChange(id);
    }
}
