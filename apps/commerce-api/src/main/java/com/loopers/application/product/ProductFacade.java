package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.ProductLikeCount;
import com.loopers.domain.product.LikesCursor;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.productrank.ProductRankRepository;
import com.loopers.domain.productrank.RankedProduct;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeCountRepository likeCountRepository;
    private final ProductRankRepository productRankRepository;
    private final ProductCachePort productCache;

    private static final int OVER_FETCH = 2; // 삭제/누락 보정용 여유분 배수
    private static final int BLOB_SIZE = 100; // 첫 페이지 hot 경로용 top-N 블롭 크기
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration LIST_TTL = Duration.ofSeconds(60); // 목록 블롭 짧은 TTL(순수 TTL 무효화)

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productRepository.save(new ProductModel(brandId, name, description, price, stock));
        return ProductInfo.from(product, 0L);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        return ProductInfo.from(loadProduct(id), likeCountOf(id));
    }

    @Transactional(readOnly = true)
    public ProductDetailInfo getProductDetail(Long id) {
        Optional<ProductDetailInfo> cached = productCache.getDetail(id);
        if (cached.isPresent()) {
            return cached.get(); // 캐시 히트
        }
        ProductModel product = loadProduct(id);
        BrandModel brand = brandRepository.find(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        ProductDetailInfo info = ProductDetailInfo.from(product, brand, likeCountOf(id));
        productCache.putDetail(info, DETAIL_TTL); // read-through
        return info;
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 정보가 올바르지 않습니다.");
        }
        List<ProductModel> products = productRepository.findAll(brandId, ProductSortType.from(sort), page, size);
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        Map<Long, Long> counts = likeCountRepository.findAllByProductIds(productIds).stream()
            .collect(Collectors.toMap(ProductLikeCount::getProductId, ProductLikeCount::getCount));
        return products.stream()
            .map(product -> ProductInfo.from(product, counts.getOrDefault(product.getId(), 0L)))
            .toList();
    }

    /**
     * likes_desc 키셋 커서 조회(읽기모델 b). rank 가 stale 라 삭제/누락 상품이 섞일 수 있어
     * 여유분(OVER_FETCH 배)을 읽어 필터하고 size 를 채운다. 커서·표시 like_count 는 모두 rank 값.
     */
    @Transactional(readOnly = true)
    public ProductCursorPage getProductsByLikesCursor(Long brandId, String cursorToken, int size) {
        if (size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size 가 올바르지 않습니다.");
        }
        LikesCursor cursor = LikesCursor.decode(cursorToken);
        int need = size * OVER_FETCH;
        RankedWindow window = loadRankedForCursor(brandId, cursor, need);
        List<RankedProduct> ranked = window.ranked();

        // 배치 조회로 N+1 제거 — rank 의 productId 들을 한 번에(미삭제만).
        List<Long> ids = ranked.stream().map(RankedProduct::productId).toList();
        Map<Long, ProductModel> products = productRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));

        List<ProductInfo> items = new ArrayList<>(size);
        RankedProduct lastRanked = null;
        for (RankedProduct r : ranked) {
            lastRanked = r;
            ProductModel product = products.get(r.productId());
            if (product == null) {
                continue; // 삭제/누락 필터
            }
            items.add(ProductInfo.from(product, r.likeCount()));
            if (items.size() == size) {
                break;
            }
        }

        // size 를 못 채웠어도 윈도가 꽉 찼으면(뒤에 더 있음) 다음 커서를 줘 페이지 유실을 막는다.
        boolean mightHaveMore = items.size() == size || window.full();
        String next = (lastRanked == null || !mightHaveMore)
            ? null
            : new LikesCursor(lastRanked.likeCount(), lastRanked.productId()).encode();
        return new ProductCursorPage(items, next);
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = loadProduct(id);
        product.update(name, description, price, stock);
        ProductInfo result = ProductInfo.from(productRepository.save(product), likeCountOf(id));
        productCache.evictDetail(id); // 상품 수정 시 상세 캐시 정밀 evict
        return result;
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = loadProduct(id);
        product.delete(); // soft delete
        productRepository.save(product);
        productCache.evictDetail(id);
    }

    /** 첫 페이지(hot)는 top-N 블롭 캐시 경유, 이후 페이지는 DB 직접(키셋). full=윈도가 한도만큼 꽉 참(뒤에 더 있을 수 있음). */
    private RankedWindow loadRankedForCursor(Long brandId, LikesCursor cursor, int need) {
        if (cursor == null && need <= BLOB_SIZE) {
            List<RankedProduct> blob = productCache.getLikesBlob(brandId).orElse(null);
            if (blob == null) {
                blob = productRankRepository.findRankedByBrandLikesDesc(brandId, null, null, BLOB_SIZE);
                productCache.putLikesBlob(brandId, blob, LIST_TTL);
            }
            return new RankedWindow(blob, blob.size() == BLOB_SIZE);
        }
        Long lastLike = cursor == null ? null : cursor.likeCount();
        Long lastId = cursor == null ? null : cursor.productId();
        List<RankedProduct> ranked = productRankRepository.findRankedByBrandLikesDesc(brandId, lastLike, lastId, need);
        return new RankedWindow(ranked, ranked.size() == need);
    }

    /** 키셋 윈도 결과. full 이면 요청 한도만큼 꽉 차 뒤에 더 있을 수 있음. */
    private record RankedWindow(List<RankedProduct> ranked, boolean full) {}

    private ProductModel loadProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    private long likeCountOf(Long productId) {
        return likeCountRepository.find(productId)
            .map(ProductLikeCount::getCount)
            .orElse(0L);
    }
}
