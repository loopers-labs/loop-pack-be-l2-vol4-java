package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.loopers.fixture.LikeModelFixture.aLike;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LikeModelTest {

    @Nested
    @DisplayName("LikeModel 생성")
    class Create {

        @DisplayName("유효한 값으로 생성하면, 활성 상태의 좋아요가 만들어진다")
        @Test
        void given_validInput_when_create_then_createsActiveLike() {
            LikeModel like = new LikeModel(1L, 2L);

            assertAll(
                    () -> assertThat(like.getUserId()).isEqualTo(1L),
                    () -> assertThat(like.getProductId()).isEqualTo(2L),
                    () -> assertThat(like.getLikedAt()).isNotNull(),
                    () -> assertThat(like.isActive()).isTrue()
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullUserId_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class,
                    () -> aLike().withUserId(null).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullProductId_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class,
                    () -> aLike().withProductId(null).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("좋아요 취소/재활성")
    class SoftDeleteAndReactivate {

        @DisplayName("delete() 하면 비활성이 된다")
        @Test
        void given_activeLike_when_delete_then_inactive() {
            LikeModel like = aLike().build();

            like.delete();

            assertThat(like.isActive()).isFalse();
        }

        @DisplayName("취소된 좋아요를 reactivate() 하면 다시 활성이 되고 likedAt이 갱신된다")
        @Test
        void given_deletedLike_when_reactivate_then_activeAgain() {
            LikeModel like = aLike().build();
            like.delete();

            like.reactivate();

            assertAll(
                    () -> assertThat(like.isActive()).isTrue(),
                    () -> assertThat(like.getLikedAt()).isNotNull()
            );
        }
    }
}
