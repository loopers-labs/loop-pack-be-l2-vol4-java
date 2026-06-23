package com.loopers.application.product;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.like.LikeFacade;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductCacheIntegrationTest {

    private final ProductFacade productFacade;
    private final BrandFacade brandFacade;
    private final LikeFacade likeFacade;
    private final ProductService productService;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long brandId;

    @Autowired
    public ProductCacheIntegrationTest(
        ProductFacade productFacade,
        BrandFacade brandFacade,
        LikeFacade likeFacade,
        ProductService productService,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.productFacade = productFacade;
        this.brandFacade = brandFacade;
        this.likeFacade = likeFacade;
        this.productService = productService;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandFacade.create("나이키", "Just Do It").id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("상품 상세를 두 번 조회하면, 두 번째는 캐시에서 받아 그 사이 DB 변경(좋아요 증가)을 반영하지 않는다. (캐시 hit)")
    @Test
    void servesDetailFromCache_onSecondCall() {
        // given
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        ProductInfo first = productFacade.getProduct(productId);   // 캐시 미스 → 적재 (likeCount 0)
        productService.incrementLikeCount(productId);              // DB 만 직접 변경(캐시 우회) → DB likeCount 1

        // when
        ProductInfo second = productFacade.getProduct(productId);  // 캐시 히트 → stale

        // then
        assertThat(first.likeCount()).isZero();
        assertThat(second.likeCount()).isZero();                  // DB 는 1 이지만 캐시(0) 를 반환 → 히트 증명
    }

    @DisplayName("Redis 를 비우면, 다음 상세 조회는 다시 DB 에서 최신 값을 받는다. (캐시 miss)")
    @Test
    void reloadsDetailFromDb_afterCacheFlush() {
        // given
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        productFacade.getProduct(productId);                       // 캐시 적재 (likeCount 0)
        productService.incrementLikeCount(productId);              // DB likeCount 1
        redisCleanUp.truncateAll();                               // 캐시 비움

        // when
        ProductInfo reloaded = productFacade.getProduct(productId); // 캐시 미스 → DB 재조회

        // then
        assertThat(reloaded.likeCount()).isEqualTo(1L);           // 최신 DB 값 반환 → 미스 증명
    }

    @DisplayName("상품 목록을 같은 조건으로 두 번 조회하면, 두 번째는 캐시에서 받아 그 사이 DB 변경을 반영하지 않는다. (캐시 hit)")
    @Test
    void servesListFromCache_onSecondCall() {
        // given
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        List<ProductInfo> first = productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20);  // 미스 → 적재 (likeCount 0)
        productService.incrementLikeCount(productId);                                                 // DB 만 변경 → likeCount 1

        // when
        List<ProductInfo> second = productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20); // 히트 → stale

        // then
        assertThat(first).singleElement().extracting(ProductInfo::likeCount).isEqualTo(0L);
        assertThat(second).singleElement().extracting(ProductInfo::likeCount).isEqualTo(0L);          // DB 는 1 이지만 캐시(0) 반환 → 히트 증명
    }

    @DisplayName("정렬 조건이 다르면 다른 캐시 키라, 한쪽을 캐시해도 다른 쪽은 최신 DB 값을 받는다. (키 분리)")
    @Test
    void usesSeparateKeyPerParams() {
        // given
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20);  // LATEST 키만 적재 (likeCount 0)
        productService.incrementLikeCount(productId);                       // DB likeCount 1

        // when
        List<ProductInfo> likesDesc = productFacade.getAllProducts(null, ProductSortType.LIKES_DESC, 0, 20); // 다른 키 → 미스 → DB

        // then
        assertThat(likesDesc).singleElement().extracting(ProductInfo::likeCount).isEqualTo(1L);     // 최신 DB 값 → 키 분리 증명
    }

    @DisplayName("상품을 수정하면, 상세 캐시가 evict 되어 다음 조회는 변경된 이름을 반영한다.")
    @Test
    void evictsDetailCacheOnUpdate() {
        // given
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        productFacade.getProduct(productId);                                                  // 상세 캐시 적재 (이름: 에어맥스 270)

        // when
        productFacade.updateProduct(productId, "에어맥스 270 SE", "스페셜 에디션", 179_000L, 50); // evict
        ProductInfo reloaded = productFacade.getProduct(productId);

        // then
        assertThat(reloaded.name()).isEqualTo("에어맥스 270 SE");                              // 캐시였으면 옛 이름, evict 됐으니 새 이름
    }

    @DisplayName("상품을 수정하면, 목록 캐시(allEntries)가 evict 되어 다음 목록 조회는 변경을 반영한다.")
    @Test
    void evictsListCacheOnUpdate() {
        // given
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20);                    // 목록 캐시 적재

        // when
        productFacade.updateProduct(productId, "에어맥스 270 SE", "스페셜 에디션", 179_000L, 50); // evict allEntries
        List<ProductInfo> reloaded = productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20);

        // then
        assertThat(reloaded).singleElement().extracting(ProductInfo::name).isEqualTo("에어맥스 270 SE");
    }

    @DisplayName("상품을 삭제하면, 상세·목록 캐시가 모두 evict 된다.")
    @Test
    void evictsCachesOnDelete() {
        // given
        Long keep = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId).id();
        Long target = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        productFacade.getProduct(target);                                    // 상세 캐시 적재
        productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20);   // 목록 캐시 적재 [target, keep]

        // when
        productFacade.deleteProduct(target);                                 // evict 상세 + 목록

        // then
        CoreException exception = assertThrows(CoreException.class, () -> productFacade.getProduct(target));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND);          // 상세 evict → DB → 삭제됨
        assertThat(productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20))
            .extracting(ProductInfo::id).containsExactly(keep);                               // 목록 evict → 삭제 상품 사라짐
    }

    @DisplayName("상품을 등록하면, 목록 캐시가 evict 되어 다음 목록 조회에 새 상품이 보인다.")
    @Test
    void evictsListCacheOnCreate() {
        // given
        Long first = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId).id();
        productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20);   // 목록 캐시 적재 [first]

        // when
        Long second = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id(); // evict allEntries
        List<ProductInfo> reloaded = productFacade.getAllProducts(null, ProductSortType.LATEST, 0, 20);

        // then
        assertThat(reloaded).extracting(ProductInfo::id).containsExactlyInAnyOrder(first, second);
    }

    @DisplayName("좋아요가 발생해도 상세 캐시는 evict 되지 않는다(stale 허용, TTL 흡수).")
    @Test
    void keepsDetailCacheOnLike() {
        // given
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
        productFacade.getProduct(productId);            // 상세 캐시 적재 (likeCount 0)

        // when
        likeFacade.like(1L, productId);                 // 실제 좋아요 경로 → like_count 증가, 캐시 evict 안 함

        // then
        ProductInfo cached = productFacade.getProduct(productId);
        assertThat(cached.likeCount()).isZero();        // DB 는 1 이지만 캐시(0) 유지 → 좋아요는 evict 하지 않음
    }
}
