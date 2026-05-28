package com.loopers.domain.like;

import com.loopers.domain.like.model.Like;
import com.loopers.domain.like.repository.LikeRepository;
import com.loopers.domain.like.service.LikeDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeDomainServiceTest {

    private LikeRepository likeRepository;
    private LikeDomainService likeDomainService;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        likeDomainService = new LikeDomainService(likeRepository);
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class AddLike {

        @DisplayName("이미 존재하는 좋아요이면, false를 반환한다.")
        @Test
        void returnsFalse_whenLikeAlreadyExists() {
            // Arrange
            when(likeRepository.existsByUserIdAndProductId(1L, 2L)).thenReturn(true);

            // Act
            boolean result = likeDomainService.addLike(1L, 2L);

            // Assert
            assertThat(result).isFalse();
        }

        @DisplayName("존재하지 않는 좋아요이면, 저장 후 true를 반환한다.")
        @Test
        void returnsTrue_whenLikeDoesNotExist() {
            // Arrange
            when(likeRepository.existsByUserIdAndProductId(1L, 2L)).thenReturn(false);
            when(likeRepository.save(org.mockito.ArgumentMatchers.any(Like.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // Act
            boolean result = likeDomainService.addLike(1L, 2L);

            // Assert
            assertThat(result).isTrue();
            verify(likeRepository).save(org.mockito.ArgumentMatchers.any(Like.class));
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class RemoveLike {

        @DisplayName("존재하지 않는 좋아요이면, false를 반환한다.")
        @Test
        void returnsFalse_whenLikeDoesNotExist() {
            // Arrange
            when(likeRepository.existsByUserIdAndProductId(1L, 2L)).thenReturn(false);

            // Act
            boolean result = likeDomainService.removeLike(1L, 2L);

            // Assert
            assertThat(result).isFalse();
        }

        @DisplayName("존재하는 좋아요이면, 삭제 후 true를 반환한다.")
        @Test
        void returnsTrue_whenLikeExists() {
            // Arrange
            when(likeRepository.existsByUserIdAndProductId(1L, 2L)).thenReturn(true);

            // Act
            boolean result = likeDomainService.removeLike(1L, 2L);

            // Assert
            assertThat(result).isTrue();
            verify(likeRepository).deleteByUserIdAndProductId(1L, 2L);
        }
    }

    @DisplayName("좋아요 목록을 조회할 때, ")
    @Nested
    class GetLikes {

        @DisplayName("요청자와 대상이 같으면, 좋아요 목록을 반환한다.")
        @Test
        void returnsLikes_whenRequestUserIsSameAsTargetUser() {
            // Arrange
            List<Like> likes = List.of(Like.create(1L, 10L), Like.create(1L, 20L));
            when(likeRepository.findAllByUserId(1L)).thenReturn(likes);

            // Act
            List<Like> result = likeDomainService.getLikes(1L, 1L);

            // Assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("요청자와 대상이 다르면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenRequestUserDiffersFromTargetUser() {
            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                likeDomainService.getLikes(1L, 2L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
