package com.loopers.domain.wishlist;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class WishlistServiceIntegrationTest {

    @Autowired private WishlistService wishlistService;
    @Autowired private WishlistRepository wishlistRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PRODUCT_ID = 100L;
    private static final Long OTHER_PRODUCT_ID = 200L;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void saveWishlist(Long userId, Long productId) {
        wishlistRepository.save(new WishlistModel(userId, productId));
    }

    @DisplayName("찜 추가 시,")
    @Nested
    class Add {

        @DisplayName("유효한 입력이면, 찜이 저장된다.")
        @Test
        void savesWishlist_whenInputsAreValid() {
            WishlistModel result = wishlistService.add(USER_ID, PRODUCT_ID);

            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("이미 찜한 상품이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyWishlisted() {
            saveWishlist(USER_ID, PRODUCT_ID);

            CoreException exception = assertThrows(CoreException.class,
                    () -> wishlistService.add(USER_ID, PRODUCT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("동시에 같은 상품을 찜하면, 한 번만 성공한다.")
        @Test
        void onlyOneSucceeds_whenConcurrentAddWithSameUserAndProduct() throws InterruptedException {
            ConcurrentResult result = runConcurrent(5, () ->
                    wishlistService.add(USER_ID, PRODUCT_ID));

            List<WishlistModel> saved = wishlistRepository.findAllByUserId(USER_ID);
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.failureCount()).isEqualTo(4);
            assertThat(saved).hasSize(1);
        }
    }

    @DisplayName("찜 삭제 시,")
    @Nested
    class Remove {

        @DisplayName("찜 목록에 존재하면, 삭제된다.")
        @Test
        void removesWishlist_whenWishlistExists() {
            saveWishlist(USER_ID, PRODUCT_ID);

            wishlistService.remove(USER_ID, PRODUCT_ID);

            List<WishlistModel> result = wishlistRepository.findAllByUserId(USER_ID);
            assertThat(result).isEmpty();
        }

        @DisplayName("찜 목록에 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenWishlistDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> wishlistService.remove(USER_ID, PRODUCT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("찜 목록 조회 시,")
    @Nested
    class GetList {

        @DisplayName("찜한 상품이 있으면, 해당 사용자의 찜 목록을 반환한다.")
        @Test
        void returnsWishlist_whenUserHasWishlists() {
            saveWishlist(USER_ID, PRODUCT_ID);
            saveWishlist(USER_ID, OTHER_PRODUCT_ID);

            List<WishlistModel> result = wishlistService.getList(USER_ID);

            assertThat(result).hasSize(2);
        }

        @DisplayName("다른 사용자의 찜은 반환되지 않는다.")
        @Test
        void excludesOtherUsersWishlists() {
            saveWishlist(USER_ID, PRODUCT_ID);
            saveWishlist(OTHER_USER_ID, OTHER_PRODUCT_ID);

            List<WishlistModel> result = wishlistService.getList(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(PRODUCT_ID);
        }
    }

    @DisplayName("상품 찜 수 단건 조회 시,")
    @Nested
    class CountByProductId {

        @DisplayName("여러 사용자가 찜한 수를 정확히 반환한다.")
        @Test
        void returnsCorrectCount_whenMultipleUsersWishlist() {
            saveWishlist(USER_ID, PRODUCT_ID);
            saveWishlist(OTHER_USER_ID, PRODUCT_ID);

            long count = wishlistRepository.countByProductId(PRODUCT_ID);

            assertThat(count).isEqualTo(2);
        }

        @DisplayName("찜이 없는 상품은 0을 반환한다.")
        @Test
        void returnsZero_whenNoWishlists() {
            long count = wishlistRepository.countByProductId(PRODUCT_ID);

            assertThat(count).isEqualTo(0L);
        }
    }

    @DisplayName("상품 목록 찜 수 일괄 조회 시,")
    @Nested
    class CountsByProductIds {

        @DisplayName("각 상품의 찜 수를 productId 기준 맵으로 반환한다.")
        @Test
        void returnsCountMapByProductId() {
            saveWishlist(USER_ID, PRODUCT_ID);
            saveWishlist(OTHER_USER_ID, PRODUCT_ID);
            saveWishlist(USER_ID, OTHER_PRODUCT_ID);

            Map<Long, Long> counts = wishlistRepository.countsByProductIds(List.of(PRODUCT_ID, OTHER_PRODUCT_ID));

            assertThat(counts.get(PRODUCT_ID)).isEqualTo(2L);
            assertThat(counts.get(OTHER_PRODUCT_ID)).isEqualTo(1L);
        }

        @DisplayName("찜이 없는 상품은 맵에 포함되지 않는다.")
        @Test
        void excludesProductsWithNoWishlists() {
            saveWishlist(USER_ID, PRODUCT_ID);

            Map<Long, Long> counts = wishlistRepository.countsByProductIds(List.of(PRODUCT_ID, OTHER_PRODUCT_ID));

            assertThat(counts).containsKey(PRODUCT_ID);
            assertThat(counts).doesNotContainKey(OTHER_PRODUCT_ID);
        }
    }

    record ConcurrentResult(int successCount, int failureCount) {}

    private ConcurrentResult runConcurrent(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    task.run();
                    successCount.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();
        return new ConcurrentResult(successCount.get(), failureCount.get());
    }
}