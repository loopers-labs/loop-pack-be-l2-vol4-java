package com.loopers.application.productlike;

import com.loopers.application.activitylog.UserActivityLogHandler;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLikedEvent;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.productlike.ProductLikeJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ProductLikeFacadeIntegrationTest {

    @Autowired
    private ProductLikeFacade productLikeFacade;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ProductLikeJpaRepository productLikeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoSpyBean
    private ProductService productService;

    @MockitoSpyBean
    private UserActivityLogHandler userActivityLogHandler;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel saveProduct() {
        return productJpaRepository.save(new ProductModel(1L, "상품", "설명", 1000L, 100));
    }

    private UserModel saveUser(String loginId) {
        return userJpaRepository.save(new UserModel(loginId, "pw1"));
    }

    /** 집계는 @Async로 비동기 반영되므로, like_count가 기대값이 될 때까지 최대 5초 대기한다. */
    private void awaitLikeCount(Long productId, long expected) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(productJpaRepository.findById(productId).orElseThrow().getLikeCount())
                .isEqualTo(expected));
    }

    @DisplayName("좋아요 등록 시,")
    @Nested
    class Like {

        @DisplayName("처음 좋아요하면 Like가 생성되고 like_count가 1 증가한다.")
        @Test
        void createsLikeAndIncreasesCount() {
            // arrange
            UserModel user = saveUser("user1");
            ProductModel product = saveProduct();

            // act
            productLikeFacade.like("user1", "pw1", product.getId());

            // assert: 좋아요는 즉시 커밋, 집계(like_count)는 비동기로 곧 반영
            assertThat(productLikeJpaRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isTrue();
            awaitLikeCount(product.getId(), 1L);
        }

        @DisplayName("같은 사용자가 2회 좋아요해도 Like는 1건, count는 1로 유지된다(멱등).")
        @Test
        void isIdempotent_whenLikedTwice() {
            // arrange
            saveUser("user1");
            ProductModel product = saveProduct();

            // act
            productLikeFacade.like("user1", "pw1", product.getId());
            productLikeFacade.like("user1", "pw1", product.getId());

            // assert: 두 번째 좋아요는 멱등(insert 0행)이라 이벤트가 없다 → 결국 count는 1
            awaitLikeCount(product.getId(), 1L);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            saveUser("user1");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                productLikeFacade.like("user1", "pw1", 999L)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요 취소 시,")
    @Nested
    class Unlike {

        @DisplayName("좋아요 상태에서 취소하면 Like가 삭제되고 like_count가 1 감소한다.")
        @Test
        void deletesLikeAndDecreasesCount() {
            // arrange: 좋아요 +1 집계가 확실히 반영된 뒤 취소해야 순서가 보장된다
            UserModel user = saveUser("user1");
            ProductModel product = saveProduct();
            productLikeFacade.like("user1", "pw1", product.getId());
            awaitLikeCount(product.getId(), 1L);

            // act
            productLikeFacade.unlike("user1", "pw1", product.getId());

            // assert: 좋아요는 즉시 삭제 커밋, 집계 -1은 비동기로 곧 반영
            assertThat(productLikeJpaRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isFalse();
            awaitLikeCount(product.getId(), 0L);
        }

        @DisplayName("좋아요 상태가 아닐 때 취소해도 무시되고 count는 음수가 되지 않는다(멱등).")
        @Test
        void isIdempotentAndNeverNegative_whenNotLiked() {
            // arrange
            saveUser("user1");
            ProductModel product = saveProduct();

            // act
            productLikeFacade.unlike("user1", "pw1", product.getId());
            productLikeFacade.unlike("user1", "pw1", product.getId());

            // assert
            assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getLikeCount()).isEqualTo(0L);
        }
    }

    @DisplayName("동시성 검증 시,")
    @Nested
    class Concurrency {

        @DisplayName("서로 다른 N명이 같은 상품에 동시에 좋아요하면 like_count는 정확히 N이 된다.")
        @Test
        void countEqualsN_whenDistinctUsersLikeConcurrently() throws InterruptedException {
            // arrange
            int threadCount = 20;
            ProductModel product = saveProduct();
            for (int i = 0; i < threadCount; i++) {
                saveUser("user" + i);
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // act
            for (int i = 0; i < threadCount; i++) {
                final String loginId = "user" + i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        productLikeFacade.like(loginId, "pw1", product.getId());
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert: 20건의 좋아요 이벤트가 비동기로 모두 반영되면 count는 정확히 N
            awaitLikeCount(product.getId(), threadCount);
        }

        @DisplayName("같은 사용자가 같은 상품에 동시에 중복 좋아요해도 Like는 1건, like_count는 1이다.")
        @Test
        void countIsOne_whenSameUserLikesConcurrently() throws InterruptedException {
            // arrange
            int threadCount = 20;
            saveUser("user1");
            ProductModel product = saveProduct();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        productLikeFacade.like("user1", "pw1", product.getId());
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert: 중복은 insert 0행이라 이벤트는 1건뿐 → count는 결국 1
            awaitLikeCount(product.getId(), 1L);
        }
    }

    @DisplayName("집계(리스너)가 실패해도,")
    @Nested
    class WhenAggregationFails {

        @DisplayName("좋아요는 커밋되고 호출자는 예외를 받지 않으며, like_count만 반영되지 않는다.")
        @Test
        void likeIsCommitted_andCallerSeesNoError() {
            // arrange: AFTER_COMMIT 리스너가 부르는 집계를 일부러 실패시킨다
            UserModel user = saveUser("user1");
            ProductModel product = saveProduct();
            doThrow(new RuntimeException("집계 실패 주입"))
                .when(productService).increaseLikeCount(product.getId());

            // act & assert: 집계가 비동기 스레드에서 실패해도 호출자에게 전파되지 않는다
            assertDoesNotThrow(() -> productLikeFacade.like("user1", "pw1", product.getId()));

            // 좋아요는 즉시 커밋됨(=트랜잭션 분리)
            assertThat(productLikeJpaRepository.existsByUserIdAndProductId(user.getId(), product.getId()))
                .as("집계 실패와 무관하게 좋아요는 커밋되어야 한다").isTrue();

            // 비동기 집계가 실제로 시도되어 예외를 던졌음을 확인(리스너가 삼킴)
            await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(productService).increaseLikeCount(product.getId()));

            // 집계가 실패했으므로 like_count는 증가하지 않는다
            assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getLikeCount())
                .as("집계가 실패했으므로 like_count는 증가하지 않는다").isEqualTo(0L);
        }
    }

    @DisplayName("좋아요 이벤트는 하나의 사실을 여러 소비자가 소비하여,")
    @Nested
    class OneFactManyConsumers {

        @DisplayName("집계 리스너(비동기)와 행동로깅 리스너(동기)가 함께 반응한다.")
        @Test
        void bothAggregationAndActivityLogReact_onLike() {
            // arrange
            saveUser("user1");
            ProductModel product = saveProduct();

            // act
            productLikeFacade.like("user1", "pw1", product.getId());

            // assert: 같은 ProductLiked를 행동로깅(동기 AFTER_COMMIT)은 즉시, 집계(@Async)는 곧 반영
            verify(userActivityLogHandler).onProductLiked(any(ProductLikedEvent.class));
            awaitLikeCount(product.getId(), 1L);
        }
    }

    @DisplayName("내 좋아요 목록 조회 시,")
    @Nested
    class GetLikedProducts {

        @DisplayName("본인의 좋아요한 상품 목록이 반환된다.")
        @Test
        void returnsOwnLikedProducts() {
            // arrange
            saveUser("user1");
            ProductModel product1 = saveProduct();
            ProductModel product2 = saveProduct();
            productLikeFacade.like("user1", "pw1", product1.getId());
            productLikeFacade.like("user1", "pw1", product2.getId());

            // act
            List<LikedProductInfo> result = productLikeFacade.getLikedProducts("user1", "pw1", "user1");

            // assert
            assertThat(result).hasSize(2)
                .extracting(LikedProductInfo::productId)
                .containsExactlyInAnyOrder(product1.getId(), product2.getId());
        }

        @DisplayName("타인의 userId로 조회하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenQueryingOthersList() {
            // arrange
            saveUser("user1");
            saveUser("user2");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                productLikeFacade.getLikedProducts("user1", "pw1", "user2")
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
