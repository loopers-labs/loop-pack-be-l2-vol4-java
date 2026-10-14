package com.loopers.concurrency;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 동시성 테스트.
 * <p>
 * 100 명의 서로 다른 사용자가 같은 상품에 동시에 좋아요 → likeCount 는 정확히 100.
 * 50 명이 좋아요, 그 후 50 명이 취소 → likeCount 0.
 * <p>
 * 검증 대상: {@code likes(user_id, product_id) UNIQUE} + {@code SET like_count = like_count + 1} atomic UPDATE.
 */
class LikeConcurrencyTest extends AbstractH2ConcurrencyTest {

    @Autowired
    private LikeService likeService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private BrandService brandService;
    @Autowired
    private com.loopers.utils.DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("100 명의 사용자가 같은 상품에 동시에 좋아요를 누르면, likeCount 는 정확히 100 이다.")
    @Test
    void likeCount_underConcurrency() throws InterruptedException {
        // arrange
        Long brandId = brandService.createBrand("나이키", "스포츠").getId();
        ProductModel product = productService.createProduct(brandId, "에어맥스", "런닝화", 100_000L, 9999, null);
        long productId = product.getId();
        int threadCount = 100;

        // act
        ConcurrencyTestSupport.Result result = ConcurrencyTestSupport.runRace(threadCount, () -> {
            long userId = Thread.currentThread().getId() + 1_000_000L; // 서로 다른 사용자
            likeService.like(userId, productId);
        });

        // assert
        ProductModel refreshed = productRepository.find(productId).orElseThrow();
        assertThat(result.success()).isEqualTo(threadCount);
        assertThat(refreshed.getLikeCount()).isEqualTo((long) threadCount);
    }

}

