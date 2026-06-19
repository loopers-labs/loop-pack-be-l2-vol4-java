package com.loopers.domain.like;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

class LikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    private LikeRepository likeRepository;
    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeRepository = new FakeLikeRepository();
        // 단위 테스트에서는 이벤트를 무시한다 (ProductLikeStat 갱신 자체는 다른 핸들러의 책임).
        ApplicationEventPublisher noopPublisher = event -> { /* no-op */ };
        likeService = new LikeService(likeRepository, noopPublisher);
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Like {

        @DisplayName("처음 등록하면, 좋아요가 저장되고 좋아요 수가 1이 된다.")
        @Test
        void savesLike_whenFirstTime() {
            likeService.like(USER_ID, PRODUCT_ID);

            assertThat(likeRepository.existsBy(USER_ID, PRODUCT_ID)).isTrue();
            assertThat(likeService.getLikeCount(PRODUCT_ID)).isEqualTo(1L);
        }

        @DisplayName("이미 좋아요한 상태에서 다시 등록해도, 멱등하게 좋아요 수는 1을 유지한다.")
        @Test
        void isIdempotent_whenAlreadyLiked() {
            likeService.like(USER_ID, PRODUCT_ID);
            likeService.like(USER_ID, PRODUCT_ID);

            assertThat(likeService.getLikeCount(PRODUCT_ID)).isEqualTo(1L);
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상태에서 취소하면, 좋아요 수가 0이 된다.")
        @Test
        void removesLike_whenLiked() {
            likeService.like(USER_ID, PRODUCT_ID);

            likeService.unlike(USER_ID, PRODUCT_ID);

            assertThat(likeRepository.existsBy(USER_ID, PRODUCT_ID)).isFalse();
            assertThat(likeService.getLikeCount(PRODUCT_ID)).isEqualTo(0L);
        }

        @DisplayName("좋아요하지 않은 상태에서 취소해도, 멱등하게 예외 없이 동작한다.")
        @Test
        void isIdempotent_whenNotLiked() {
            likeService.unlike(USER_ID, PRODUCT_ID);

            assertThat(likeService.getLikeCount(PRODUCT_ID)).isEqualTo(0L);
        }
    }

    @DisplayName("내가 좋아요한 상품 목록을 조회하면, 좋아요한 상품 ID들이 반환된다.")
    @Test
    void returnsLikedProductIds() {
        likeService.like(USER_ID, 100L);
        likeService.like(USER_ID, 200L);
        likeService.like(2L, 300L); // 다른 유저

        assertThat(likeService.getLikedProductIds(USER_ID)).containsExactlyInAnyOrder(100L, 200L);
    }
}
