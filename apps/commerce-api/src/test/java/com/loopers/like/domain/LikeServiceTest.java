package com.loopers.like.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeServiceTest {

    private LikeService likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeService();
    }

    @DisplayName("cancelLike를 호출할 때,")
    @Nested
    class CancelLike {

        @DisplayName("좋아요가 존재하면, 해당 LikeModel을 반환한다.")
        @Test
        void returnsLikeModel_whenLikeExists() {
            // arrange
            LikeModel existing = new LikeModel(1L, 2L);

            // act
            LikeModel result = likeService.cancelLike(Optional.of(existing));

            // assert
            assertThat(result).isEqualTo(existing);
        }

        @DisplayName("좋아요가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLikeNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                likeService.cancelLike(Optional.empty())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("createLike를 호출할 때,")
    @Nested
    class CreateLike {

        // 중복 검증은 LikeRegistrationPolicy 책임 → LikeRegistrationPolicyTest에서 검증
        @DisplayName("userId, productId가 주어지면, LikeModel을 반환한다.")
        @Test
        void returnsLikeModel_whenCalled() {
            // act
            LikeModel result = likeService.createLike(1L, 2L);

            // assert
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo(1L),
                () -> assertThat(result.getProductId()).isEqualTo(2L)
            );
        }
    }
}
