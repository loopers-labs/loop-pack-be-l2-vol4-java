package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 집계 분리(Round 7) 통합 테스트.
 *
 * <p>좋아요는 동기로 즉시 성공하고, like_count 집계는 {@code @Async} +
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 커밋 이후 별도 스레드/트랜잭션에서
 * <strong>최종 일관성</strong>으로 반영한다. 집계는 like()/unlike() 반환 직후가 아니라 잠시 뒤
 * 반영되므로 폴링으로 대기해 검증한다.
 */
@SpringBootTest
class LikeEventIntegrationTest {

    @Autowired private LikeApplicationService likeApplicationService;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록하면 likes 가 즉시 저장되고, 커밋 후 리스너가 like_count 를 1 증가시킨다.")
    @Test
    void likePersistsAndListenerIncrementsCount() {
        // arrange
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));

        // act
        likeApplicationService.like(1L, product.getId());

        // assert — likes 저장은 동기(즉시), like_count 는 비동기 집계(폴링 대기)
        assertThat(likeRepository.existsByUserIdAndProductId(1L, product.getId())).isTrue();
        assertThat(awaitLikeCount(product.getId(), 1L)).isEqualTo(1L);
    }

    @DisplayName("좋아요를 취소하면 커밋 후 리스너가 like_count 를 1 감소시킨다.")
    @Test
    void unlikeListenerDecrementsCount() {
        // arrange — 먼저 좋아요 (count=1 반영 대기)
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));
        likeApplicationService.like(1L, product.getId());
        assertThat(awaitLikeCount(product.getId(), 1L)).isEqualTo(1L);

        // act
        likeApplicationService.unlike(1L, product.getId());

        // assert
        assertThat(likeRepository.existsByUserIdAndProductId(1L, product.getId())).isFalse();
        assertThat(awaitLikeCount(product.getId(), 0L)).isEqualTo(0L);
    }

    @DisplayName("이미 좋아요한 상품에 다시 좋아요해도(멱등) like_count 는 1 을 유지한다.")
    @Test
    void idempotentLikeKeepsCountAtOne() {
        // arrange
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));

        // act — 같은 유저가 두 번 좋아요. 첫 번째 집계 반영을 기다린 뒤 두 번째(무변경 → 이벤트 미발행)를 실행해
        //        "1 에서 더 오르지 않음"을 안정적으로 검증한다.
        likeApplicationService.like(1L, product.getId());
        assertThat(awaitLikeCount(product.getId(), 1L)).isEqualTo(1L);
        likeApplicationService.like(1L, product.getId());

        // assert — 멱등: count 는 1 유지
        assertThat(awaitLikeCount(product.getId(), 1L)).isEqualTo(1L);
    }

    /**
     * 비동기 집계가 반영될 때까지 like_count 를 폴링한다. expected 도달 시 즉시 반환하고,
     * 타임아웃되면 마지막으로 읽은 값을 반환한다(불일치 시 호출부 단언에서 실패로 드러남).
     */
    private long awaitLikeCount(Long productId, long expected) {
        long deadline = System.currentTimeMillis() + 10_000L;
        long actual = -1L;
        while (System.currentTimeMillis() < deadline) {
            actual = productRepository.findById(productId).orElseThrow().getLikeCount();
            if (actual == expected) {
                return actual;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return actual;
    }
}
