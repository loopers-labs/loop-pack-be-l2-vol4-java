package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductCursor;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.stock.StockService;
import com.loopers.support.page.ProductCursorCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final StockService stockService;
    private final ProductReadCache productReadCache;
    private final ProductCursorCodec cursorCodec;

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
        return ProductInfo.of(product, stock);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.of(product, stockService.getQuantity(id));
    }

    /**
     * 상품 상세 (UC-04) — 사용자 무관 조합(Product+Brand+재고)은 캐시(ProductReadCache)에서 가져오고,
     * 사용자별 좋아요 여부(liked)만 캐시 밖에서 실시간 조합한다. 식별된 User만 liked를 본다.
     */
    public ProductDetailInfo getProductDetail(Long id, Long userId) {
        CachedProductDetail base = productReadCache.getDetail(id);
        boolean liked = userId != null && likeService.isLiked(userId, id);
        return base.toInfo(liked);
    }

    /**
     * 상품 목록 (UC-03) — 정렬·키셋(커서)·브랜드 필터 + 브랜드명·재고 조합은 캐시에서 가져오고(사용자 무관),
     * 좋아요 여부만 식별된 User에 한해 한 번의 batch 조회로 조합한다(N+1 회피).
     *
     * <p>캐시는 hasNext 판별용으로 size+1 건까지 들고 있다. 여기서 size로 잘라 노출 페이지를 만들고,
     * 잘림이 있었으면(=다음 페이지 존재) 마지막 노출 항목으로 불투명 {@code nextCursor}를 만든다.
     */
    public ProductListResult getProducts(Long brandId, ProductSortType sort, String cursor, int size, Long userId) {
        ProductSortType effectiveSort = (sort == null) ? ProductSortType.LATEST : sort;
        List<CachedProductListItem> fetched = productReadCache.getList(brandId, effectiveSort, cursor, size);

        boolean hasNext = fetched.size() > size;
        List<CachedProductListItem> pageItems = hasNext ? fetched.subList(0, size) : fetched;
        if (pageItems.isEmpty()) {
            return new ProductListResult(List.of(), null, false);
        }

        Set<Long> likedIds = (userId == null)
            ? Set.of()
            : likeService.findLikedProductIds(userId, pageItems.stream().map(CachedProductListItem::id).toList());
        List<ProductListItemInfo> items = pageItems.stream()
            .map(item -> item.toInfo(likedIds.contains(item.id())))
            .toList();

        String nextCursor = hasNext
            ? cursorCodec.encode(nextCursorOf(effectiveSort, pageItems.get(pageItems.size() - 1)))
            : null;
        return new ProductListResult(items, nextCursor, hasNext);
    }

    /** 다음 페이지 커서 — 마지막 노출 항목의 (정렬값, id). LATEST는 정렬값이 id 단독이라 null. */
    private static ProductCursor nextCursorOf(ProductSortType sort, CachedProductListItem last) {
        Long sortValue = switch (sort) {
            case LIKES_DESC -> last.likesCount();
            case PRICE_ASC, PRICE_DESC -> last.price();
            case LATEST -> null;
        };
        return new ProductCursor(sort, sortValue, last.id());
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
        return ProductInfo.of(product, stock);
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
