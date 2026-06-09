package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LikeModelTest {

    @DisplayName("좋아요 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("회원 ID와 상품 ID를 입력하면 좋아요가 생성된다.")
        @Test
        void creates_with_member_and_product_id() {
            LikeModel like = new LikeModel(1L, 2L);

            assertAll(
                () -> assertThat(like.getMemberId()).isEqualTo(1L),
                () -> assertThat(like.getProductId()).isEqualTo(2L),
                () -> assertThat(like.getDeletedAt()).isNull()
            );
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Delete {

        @DisplayName("delete()를 호출하면 deletedAt이 설정된다.")
        @Test
        void sets_deleted_at_when_deleted() {
            LikeModel like = new LikeModel(1L, 2L);

            like.delete();

            assertThat(like.getDeletedAt()).isNotNull();
        }

        @DisplayName("이미 삭제된 좋아요에 delete()를 호출해도 deletedAt이 변경되지 않는다.")
        @Test
        void delete_is_idempotent() {
            LikeModel like = new LikeModel(1L, 2L);
            like.delete();
            var firstDeletedAt = like.getDeletedAt();

            like.delete();

            assertThat(like.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }
}