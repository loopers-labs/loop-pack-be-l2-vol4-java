package com.loopers.concurrency;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.ProductLikeCount;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.like.ProductLikeCountJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 좋아요 집계 동시성 검증 — 조건부 UPDATE(upsert) 가 read-modify-write 의
 * Lost Update 를 제거해, 동시 요청 전원이 성공하고 카운트가 정확해야 한다.
 */
@SpringBootTest
class LikeConcurrencyTest {

    private static final int USER_COUNT = 20;

    private final LikeFacade likeFacade;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final LikeJpaRepository likeJpaRepository;
    private final ProductLikeCountJpaRepository productLikeCountJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;
    private List<String> loginIds;

    @Autowired
    LikeConcurrencyTest(
        LikeFacade likeFacade,
        UserJpaRepository userJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        LikeJpaRepository likeJpaRepository,
        ProductLikeCountJpaRepository productLikeCountJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.likeJpaRepository = likeJpaRepository;
        this.productLikeCountJpaRepository = productLikeCountJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        loginIds = new ArrayList<>();
        for (int i = 1; i <= USER_COUNT; i++) {
            String loginId = String.format("user%02d", i);
            userJpaRepository.save(new UserModel(
                loginId, "Password1!", "유저" + i, "1990-05-14", loginId + "@example.com", Gender.M));
            loginIds.add(loginId);
        }
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "Just Do It"));
        productId = productJpaRepository.save(new ProductModel(brand.getId(), "에어맥스", "운동화", 1000L, 10)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void runConcurrently(int threadCount, IntConsumer task,
                                 AtomicInteger success, AtomicInteger fail) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    start.await(); // 동시 출발 보장
                    task.accept(index);
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private long currentCount() {
        return productLikeCountJpaRepository.findByProductId(productId)
            .map(ProductLikeCount::getCount)
            .orElse(0L);
    }

    @DisplayName("동시에 20명이 좋아요를 눌러도, 전원 성공하고 좋아요 수는 정확히 20이다.")
    @Test
    void likeCount_isExact_underConcurrentLikes() throws InterruptedException {
        // arrange
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        // act
        runConcurrently(USER_COUNT, i -> likeFacade.like(loginIds.get(i), productId), success, fail);

        // assert
        assertAll(
            () -> assertThat(success.get()).isEqualTo(USER_COUNT),
            () -> assertThat(fail.get()).isZero(),
            () -> assertThat(currentCount()).isEqualTo(USER_COUNT),
            () -> assertThat(likeJpaRepository.count()).isEqualTo(USER_COUNT)
        );
    }

    @DisplayName("전원이 좋아요한 상태에서 동시에 20명이 취소해도, 좋아요 수는 정확히 0이다.")
    @Test
    void likeCount_isExact_underConcurrentUnlikes() throws InterruptedException {
        // arrange — 순차 등록으로 20 누적
        loginIds.forEach(loginId -> likeFacade.like(loginId, productId));
        assertThat(currentCount()).isEqualTo(USER_COUNT);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        // act
        runConcurrently(USER_COUNT, i -> likeFacade.unlike(loginIds.get(i), productId), success, fail);

        // assert
        assertAll(
            () -> assertThat(success.get()).isEqualTo(USER_COUNT),
            () -> assertThat(fail.get()).isZero(),
            () -> assertThat(currentCount()).isZero(),
            () -> assertThat(likeJpaRepository.count()).isZero()
        );
    }
}
