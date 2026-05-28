package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeService likeService;

    @DisplayName("좋아요 등록 시, ")
    @Nested
    class Like {

        @DisplayName("Like 가 신규로 저장되면 true 를 반환한다.")
        @Test
        void returnsTrue_whenLikeIsNewlySaved() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            given(likeRepository.saveIfAbsent(any(LikeModel.class))).willReturn(true);

            // when
            boolean inserted = likeService.like(userId, productId);

            // then
            assertThat(inserted).isTrue();
            verify(likeRepository).saveIfAbsent(any(LikeModel.class));
        }

        @DisplayName("이미 같은 Like 가 존재해 저장되지 않으면 false 를 반환한다 (멱등).")
        @Test
        void returnsFalse_whenLikeAlreadyExists() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            given(likeRepository.saveIfAbsent(any(LikeModel.class))).willReturn(false);

            // when
            boolean inserted = likeService.like(userId, productId);

            // then
            assertThat(inserted).isFalse();
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    class Unlike {

        @DisplayName("Like 가 실제로 삭제되면 true 를 반환한다.")
        @Test
        void returnsTrue_whenLikeIsDeleted() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            given(likeRepository.deleteByUserIdAndProductId(userId, productId)).willReturn(1);

            // when
            boolean deleted = likeService.unlike(userId, productId);

            // then
            assertThat(deleted).isTrue();
            verify(likeRepository).deleteByUserIdAndProductId(userId, productId);
        }

        @DisplayName("삭제할 Like 가 존재하지 않으면 false 를 반환한다 (멱등).")
        @Test
        void returnsFalse_whenLikeDoesNotExist() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            given(likeRepository.deleteByUserIdAndProductId(userId, productId)).willReturn(0);

            // when
            boolean deleted = likeService.unlike(userId, productId);

            // then
            assertThat(deleted).isFalse();
        }
    }

    @DisplayName("내가 좋아요한 active 상품 목록을 조회할 때, ")
    @Nested
    class GetMyLikedActiveProducts {

        @DisplayName("Repository 에서 받은 결과를 그대로 반환한다.")
        @Test
        void returnsRepositoryResult_asIs() {
            // given
            Long userId = 1L;
            int page = 0;
            int size = 20;
            ProductModel p1 = new ProductModel("에어맥스 270", "데일리", 159_000L, 10L);
            ProductModel p2 = new ProductModel("페가수스 40", "쿠셔닝", 139_000L, 10L);
            given(likeRepository.findLikedActiveProductsByUserId(userId, page, size))
                .willReturn(List.of(p1, p2));

            // when
            List<ProductModel> result = likeService.getMyLikedActiveProducts(userId, page, size);

            // then
            assertThat(result).containsExactly(p1, p2);
            verify(likeRepository).findLikedActiveProductsByUserId(userId, page, size);
        }

        @DisplayName("좋아요한 active 상품이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikedActiveProducts() {
            // given
            Long userId = 1L;
            int page = 0;
            int size = 20;
            given(likeRepository.findLikedActiveProductsByUserId(userId, page, size))
                .willReturn(List.of());

            // when
            List<ProductModel> result = likeService.getMyLikedActiveProducts(userId, page, size);

            // then
            assertThat(result).isEmpty();
        }
    }
}
