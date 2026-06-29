package com.loopers.application.like;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.infrastructure.like.LikeJpaRepository;
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

/**
 * 동시성 하에서 likes 행이 정확히 수렴하는지(유실/중복 없음)를 검증한다.
 *
 * like_count 의 정합성 단언은 이 테스트에서 의도적으로 빠졌다 — 좋아요 카운트의 집이
 * product_metrics(streamer 읽기모델)로 이사했기 때문(결정 #1). 카운트 수렴은
 * eventual consistency 로 streamer Consumer 테스트가 await 하며 검증한다.
 * 여기 api 쪽은 "좋아요 자체"의 동시성 불변식(likes 행)만 책임진다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LikeFacadeConcurrencyTest {

    private final LikeFacade likeFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final LikeJpaRepository likeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public LikeFacadeConcurrencyTest(
        LikeFacade likeFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        LikeJpaRepository likeJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.likeJpaRepository = likeJpaRepository;
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

    @DisplayName("서로 다른 100 명의 사용자가 동시에 좋아요를 누르면, likes 행이 정확히 100 개 생성된다.")
    @Test
    void persistsAllLikes_whenHundredUsersLikeConcurrently() throws InterruptedException {
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
        assertThat(likeJpaRepository.count()).isEqualTo(100L);
    }

    @DisplayName("이미 좋아요한 서로 다른 100 명이 동시에 좋아요를 취소하면, likes 행이 정확히 0 개가 된다.")
    @Test
    void cancelsAllLikes_whenManyUsersUnlikeConcurrently() throws InterruptedException {
        // given
        int userCount = 100;
        for (long i = 1; i <= userCount; i++) {
            likeFacade.like(i, productId);
        }
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch done = new CountDownLatch(userCount);

        // when
        for (long i = 1; i <= userCount; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    likeFacade.unlike(userId, productId);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(likeJpaRepository.count()).isEqualTo(0L);
    }

    @DisplayName("새로운 k 명의 좋아요와 미리 좋아요한 k 명의 취소가 동시에 일어나도, likes 행이 정확히 k 개로 수렴한다.")
    @Test
    void keepsLikesConsistent_whenLikesAndUnlikesRunConcurrently() throws InterruptedException {
        // given
        int k = 50;
        // 1..k 는 미리 좋아요(동시 취소 대상), k+1..2k 는 새로 좋아요(동시 추가 대상)
        for (long i = 1; i <= k; i++) {
            likeFacade.like(i, productId);
        }
        int threadCount = 2 * k;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);

        // when
        for (long i = 1; i <= k; i++) {
            final long unlikeUserId = i;
            final long likeUserId = k + i;
            executor.submit(() -> {
                try {
                    likeFacade.unlike(unlikeUserId, productId);
                } finally {
                    done.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    likeFacade.like(likeUserId, productId);
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(likeJpaRepository.count()).isEqualTo((long) k);
    }
}
