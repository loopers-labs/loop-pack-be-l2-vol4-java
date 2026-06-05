package com.loopers.application.like;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LikeApplicationServiceIntegrationTest {

    private static final Long USER_A = 100L;
    private static final Long USER_B = 200L;

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
        productId = productJpaRepository.save(
                Product.create(brandId, "상품1", Money.of(1_000L), Stock.of(10))).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("register 는 ")
    @Nested
    class Register {

        @DisplayName("아직 좋아요하지 않은 상품에 좋아요를 누르면 좋아요 행이 생기고 수가 1 늘어난다. (AC-04-1)")
        @Test
        void createsLike_whenNotLikedBefore() {
            likeApplicationService.register(USER_A, productId);

            assertThat(likeRepository.exists(USER_A, productId)).isTrue();
            assertThat(likeRepository.countByProductId(productId)).isEqualTo(1L);
        }

        @DisplayName("이미 좋아요한 상품에 다시 등록 요청하면 오류 없이 처리되고 좋아요 수는 그대로다. (AC-04-2 멱등)")
        @Test
        void isIdempotent_whenAlreadyLiked() {
            likeApplicationService.register(USER_A, productId);
            likeApplicationService.register(USER_A, productId);
            likeApplicationService.register(USER_A, productId);

            assertThat(likeRepository.countByProductId(productId)).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 상품에 좋아요를 누르면 NOT_FOUND. (AC-04-3)")
        @Test
        void throwsNotFound_whenProductMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> likeApplicationService.register(USER_A, 99999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("논리 삭제된 상품에 좋아요를 누르면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenProductDeleted() {
            Product deleted = productJpaRepository.save(
                    Product.create(brandJpaRepository.findAll().get(0).getId(), "삭제상품", Money.of(1_000L), Stock.of(10)));
            deleted.delete();
            productJpaRepository.save(deleted);

            CoreException result = assertThrows(CoreException.class,
                    () -> likeApplicationService.register(USER_A, deleted.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("같은 상품에 두 사용자가 좋아요를 누르면 좋아요 수는 2 가 된다.")
        @Test
        void countsLikesAcrossUsers() {
            likeApplicationService.register(USER_A, productId);
            likeApplicationService.register(USER_B, productId);

            assertThat(likeRepository.countByProductId(productId)).isEqualTo(2L);
        }

        @DisplayName("같은 (user, product) 로 동시에 여러 요청이 들어와도 예외 없이 1행만 생성된다. (AC-04-2 멱등 — DB PK 최종 방어선)")
        @Test
        void isIdempotent_underConcurrency() throws InterruptedException {
            int threads = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger failures = new AtomicInteger();
            List<String> errors = new CopyOnWriteArrayList<>();

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        likeApplicationService.register(USER_A, productId);
                    } catch (Throwable t) {
                        failures.incrementAndGet();
                        errors.add(t.getClass().getName() + ": " + t.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdownNow();

            assertThat(failures.get()).as("실패한 호출: %s", errors).isZero();
            assertThat(likeRepository.countByProductId(productId)).isEqualTo(1L);
            assertThat(likeRepository.exists(USER_A, productId)).isTrue();
        }
    }

    @DisplayName("cancel 은 ")
    @Nested
    class Cancel {

        @DisplayName("좋아요한 상품을 취소하면 행이 제거되고 좋아요 수가 1 줄어든다. (AC-05-1)")
        @Test
        void removesLike() {
            likeApplicationService.register(USER_A, productId);
            assertThat(likeRepository.countByProductId(productId)).isEqualTo(1L);

            likeApplicationService.cancel(USER_A, productId);

            assertThat(likeRepository.exists(USER_A, productId)).isFalse();
            assertThat(likeRepository.countByProductId(productId)).isZero();
        }

        @DisplayName("좋아요하지 않은 상품에 취소 요청을 보내도 오류 없이 처리된다. (AC-05-2 멱등)")
        @Test
        void isIdempotent_whenNotLiked() {
            likeApplicationService.cancel(USER_A, productId);
            assertThat(likeRepository.exists(USER_A, productId)).isFalse();
        }

        @DisplayName("존재하지 않는 상품에 취소 요청을 보내도 오류 없이 처리된다. (AC-05-4 멱등)")
        @Test
        void isIdempotent_whenProductMissing() {
            likeApplicationService.cancel(USER_A, 99999L);
        }

        @DisplayName("연속 취소도 멱등이다.")
        @Test
        void multipleCancelsAreIdempotent() {
            likeApplicationService.register(USER_A, productId);
            likeApplicationService.cancel(USER_A, productId);
            likeApplicationService.cancel(USER_A, productId);
            likeApplicationService.cancel(USER_A, productId);

            assertThat(likeRepository.countByProductId(productId)).isZero();
        }
    }

    @DisplayName("getMyLikes 는 ")
    @Nested
    class GetMyLikes {

        @DisplayName("본인이 좋아요한 상품 목록을 페이징해서 돌려준다. (AC-06-1)")
        @Test
        void returnsOwnersLikes() {
            Long brandId = brandJpaRepository.findAll().get(0).getId();
            Long p2 = productJpaRepository.save(Product.create(brandId, "상품2", Money.of(1_000L), Stock.of(10))).getId();
            Long p3 = productJpaRepository.save(Product.create(brandId, "상품3", Money.of(1_000L), Stock.of(10))).getId();

            likeApplicationService.register(USER_A, productId);
            likeApplicationService.register(USER_A, p2);
            likeApplicationService.register(USER_A, p3);

            PageResult<LikeInfo> result = likeApplicationService.getMyLikes(USER_A, 0, 20);

            assertThat(result.content()).hasSize(3);
            assertThat(result.content()).allMatch(info -> info.userId().equals(USER_A));
            assertThat(result.totalElements()).isEqualTo(3L);
        }

        @DisplayName("다른 사용자의 좋아요는 결과에 포함되지 않는다.")
        @Test
        void excludesOtherUsersLikes() {
            likeApplicationService.register(USER_A, productId);
            likeApplicationService.register(USER_B, productId);

            PageResult<LikeInfo> result = likeApplicationService.getMyLikes(USER_A, 0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).userId()).isEqualTo(USER_A);
        }

        @DisplayName("페이지 사이즈에 맞춰 잘리고 hasNext 를 노출한다.")
        @Test
        void paginates() {
            Long brandId = brandJpaRepository.findAll().get(0).getId();
            for (int i = 0; i < 5; i++) {
                Long pid = productJpaRepository.save(
                        Product.create(brandId, "상품" + i, Money.of(1_000L), Stock.of(10))).getId();
                likeApplicationService.register(USER_A, pid);
            }

            PageResult<LikeInfo> page0 = likeApplicationService.getMyLikes(USER_A, 0, 2);

            assertThat(page0.content()).hasSize(2);
            assertThat(page0.hasNext()).isTrue();
            assertThat(page0.totalElements()).isEqualTo(5L);
        }
    }

}
