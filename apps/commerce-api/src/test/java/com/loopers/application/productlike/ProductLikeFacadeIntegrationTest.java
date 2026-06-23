package com.loopers.application.productlike;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.productlike.ProductLikeJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

            // assert
            assertAll(
                () -> assertThat(productLikeJpaRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isTrue(),
                () -> assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getLikeCount()).isEqualTo(1L)
            );
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

            // assert
            assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getLikeCount()).isEqualTo(1L);
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
            // arrange
            UserModel user = saveUser("user1");
            ProductModel product = saveProduct();
            productLikeFacade.like("user1", "pw1", product.getId());

            // act
            productLikeFacade.unlike("user1", "pw1", product.getId());

            // assert
            assertAll(
                () -> assertThat(productLikeJpaRepository.existsByUserIdAndProductId(user.getId(), product.getId())).isFalse(),
                () -> assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getLikeCount()).isEqualTo(0L)
            );
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

            // assert
            assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getLikeCount())
                .isEqualTo((long) threadCount);
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

            // assert
            assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getLikeCount())
                .isEqualTo(1L);
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
