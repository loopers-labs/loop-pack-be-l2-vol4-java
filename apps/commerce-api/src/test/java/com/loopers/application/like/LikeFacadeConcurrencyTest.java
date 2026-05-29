package com.loopers.application.like;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LikeFacadeConcurrencyTest {

    private final LikeFacade likeFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final LikeJpaRepository likeJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public LikeFacadeConcurrencyTest(
        LikeFacade likeFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        LikeJpaRepository likeJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.likeJpaRepository = likeJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("서로 다른 100 명의 사용자가 동시에 좋아요를 누르면, likes 행이 100 개이고 product 의 like_count 도 정확히 100 이다.")
    @Test
    void persistsAllLikesAndCountsExactly_whenHundredUsersLikeConcurrently() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);

        // when
        for (long i = 1; i <= threadCount; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    likeFacade.like(userId, productId);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertAll(
            () -> assertThat(likeJpaRepository.count()).isEqualTo(100L),
            () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getLikeCount()).isEqualTo(100L)
        );
    }
}
