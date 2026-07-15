package com.loopers.application.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductLikeServiceIntegrationTest {

    @Autowired
    private ProductLikeService productLikeService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createProduct() {
        return productJpaRepository.save(new ProductModel("상품", "설명", 1000L, 10, 1L)).getId();
    }

    private long likeCountOf(Long productId) {
        return productJpaRepository.findById(productId).orElseThrow().getLikeCount();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class Like {
        @DisplayName("좋아요를 등록하면, like_count 가 1 증가한다.")
        @Test
        void increasesLikeCount_whenLiked() {
            // arrange
            Long productId = createProduct();

            // act
            boolean changed = productLikeService.like("user-1", productId);

            // assert
            assertAll(
                () -> assertThat(changed).isTrue(),
                () -> assertThat(likeCountOf(productId)).isEqualTo(1L)
            );
        }

        @DisplayName("같은 사용자가 중복 등록해도, like_count 는 1 로 유지된다. (멱등)")
        @Test
        void isIdempotent_whenLikedTwice() {
            // arrange
            Long productId = createProduct();

            // act
            productLikeService.like("user-1", productId);
            boolean changedOnSecond = productLikeService.like("user-1", productId);

            // assert
            assertAll(
                () -> assertThat(changedOnSecond).isFalse(),
                () -> assertThat(likeCountOf(productId)).isEqualTo(1L)
            );
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> productLikeService.like("user-1", 999_999L));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("N 명이 동시에 좋아요를 눌러도, like_count 는 정확히 N 이 된다.")
        @Test
        void countsExactly_whenConcurrentUsersLike() throws InterruptedException {
            // arrange
            Long productId = createProduct();
            int userCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(32);
            CountDownLatch latch = new CountDownLatch(userCount);

            // act
            for (int i = 0; i < userCount; i++) {
                String userId = "user-" + i;
                executor.submit(() -> {
                    try {
                        productLikeService.like(userId, productId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            assertThat(likeCountOf(productId)).isEqualTo(userCount);
        }

        @DisplayName("같은 사용자의 동시 중복 등록에도, like_count 는 1 로 유지된다.")
        @Test
        void countsOnce_whenSameUserLikesConcurrently() throws InterruptedException {
            // arrange
            Long productId = createProduct();
            int attempts = 50;
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch latch = new CountDownLatch(attempts);

            // act
            for (int i = 0; i < attempts; i++) {
                executor.submit(() -> {
                    try {
                        productLikeService.like("user-1", productId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            assertThat(likeCountOf(productId)).isEqualTo(1L);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {
        @DisplayName("좋아요를 취소하면, like_count 가 1 감소한다.")
        @Test
        void decreasesLikeCount_whenUnliked() {
            // arrange
            Long productId = createProduct();
            productLikeService.like("user-1", productId);

            // act
            boolean changed = productLikeService.unlike("user-1", productId);

            // assert
            assertAll(
                () -> assertThat(changed).isTrue(),
                () -> assertThat(likeCountOf(productId)).isEqualTo(0L)
            );
        }

        @DisplayName("좋아요 상태가 아니면, like_count 변경 없이 성공한다. (멱등)")
        @Test
        void isIdempotent_whenNotLiked() {
            // arrange
            Long productId = createProduct();

            // act
            boolean changed = productLikeService.unlike("user-1", productId);

            // assert
            assertAll(
                () -> assertThat(changed).isFalse(),
                () -> assertThat(likeCountOf(productId)).isEqualTo(0L)
            );
        }

        @DisplayName("같은 사용자의 동시 취소 요청에도, like_count 는 음수가 되지 않고 정확히 1 만 감소한다.")
        @Test
        void decreasesOnce_whenSameUserUnlikesConcurrently() throws InterruptedException {
            // arrange
            Long productId = createProduct();
            productLikeService.like("user-1", productId);
            int attempts = 50;
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch latch = new CountDownLatch(attempts);

            // act
            for (int i = 0; i < attempts; i++) {
                executor.submit(() -> {
                    try {
                        productLikeService.unlike("user-1", productId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            assertThat(likeCountOf(productId)).isEqualTo(0L);
        }

        @DisplayName("N 명이 좋아요 후 동시에 취소하면, like_count 는 0 이 된다.")
        @Test
        void returnsToZero_whenAllUsersUnlikeConcurrently() throws InterruptedException {
            // arrange
            Long productId = createProduct();
            int userCount = 50;
            for (int i = 0; i < userCount; i++) {
                productLikeService.like("user-" + i, productId);
            }
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch latch = new CountDownLatch(userCount);

            // act
            for (int i = 0; i < userCount; i++) {
                String userId = "user-" + i;
                executor.submit(() -> {
                    try {
                        productLikeService.unlike(userId, productId);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            assertThat(likeCountOf(productId)).isEqualTo(0L);
        }
    }
}
