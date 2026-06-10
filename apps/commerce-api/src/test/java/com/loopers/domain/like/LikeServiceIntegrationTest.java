package com.loopers.domain.like;

import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LikeService 통합 — like row 영속/멱등성만 검증.
 * <p>상품 likeCount 갱신은 Facade 합성 책임이므로 {@code LikeFacadeIntegrationTest}에서 검증한다.
 * (FK 제약 미사용 — 명세 D12 — 이라 product setup 없이 임의 productId 사용 가능.)</p>
 */
@SpringBootTest
class LikeServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록 시")
    @Nested
    class Like {

        @DisplayName("새 좋아요면 product_like 행이 추가되고 true를 반환한다")
        @Test
        void persistsRow_whenNotLikedYet() {
            // when
            boolean created = likeService.like(USER_ID, PRODUCT_ID);

            // then
            assertThat(created).isTrue();
            assertThat(likeJpaRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).isTrue();
        }

        @DisplayName("이미 좋아요한 상태에서 다시 등록해도 멱등으로 row가 1개로 유지되고 false를 반환한다")
        @Test
        void isIdempotent_whenAlreadyLiked() {
            // given
            likeService.like(USER_ID, PRODUCT_ID);

            // when
            boolean created = likeService.like(USER_ID, PRODUCT_ID);

            // then
            assertThat(created).isFalse();
            assertThat(likeJpaRepository.count()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 취소 시")
    @Nested
    class Unlike {

        @DisplayName("실제로 삭제되면 row가 사라지고 true를 반환한다")
        @Test
        void deletesRow_whenLiked() {
            // given
            likeService.like(USER_ID, PRODUCT_ID);

            // when
            boolean deleted = likeService.unlike(USER_ID, PRODUCT_ID);

            // then
            assertThat(deleted).isTrue();
            assertThat(likeJpaRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).isFalse();
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 멱등으로 false를 반환한다")
        @Test
        void isIdempotent_whenNothingToUnlike() {
            // when
            boolean deleted = likeService.unlike(USER_ID, PRODUCT_ID);

            // then
            assertThat(deleted).isFalse();
        }
    }
}
