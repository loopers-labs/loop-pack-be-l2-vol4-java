package com.loopers.application.like;

import com.loopers.domain.like.service.LikeDomainService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LikeApplicationServiceTest {

    private LikeDomainService likeDomainService;
    private ProductRepository productRepository;
    private LikeApplicationService likeApplicationService;

    @BeforeEach
    void setUp() {
        likeDomainService = mock(LikeDomainService.class);
        productRepository = mock(ProductRepository.class);
        likeApplicationService = new LikeApplicationService(likeDomainService, productRepository);
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class AddLike {

        @DisplayName("상품이 존재하고 좋아요가 없으면, likeCount가 증가한다.")
        @Test
        void incrementsLikeCount_whenLikeIsNew() {
            // Arrange
            Product product = Product.create(1L, "에어맥스", "운동화", 100_000L);
            when(productRepository.findById(2L)).thenReturn(Optional.of(product));
            when(likeDomainService.addLike(1L, 2L)).thenReturn(true);

            // Act
            likeApplicationService.addLike(1L, 2L);

            // Assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("이미 좋아요가 존재하면, likeCount가 변경되지 않는다.")
        @Test
        void doesNotChangeLikeCount_whenLikeAlreadyExists() {
            // Arrange
            Product product = Product.create(1L, "에어맥스", "운동화", 100_000L);
            when(productRepository.findById(2L)).thenReturn(Optional.of(product));
            when(likeDomainService.addLike(1L, 2L)).thenReturn(false);

            // Act
            likeApplicationService.addLike(1L, 2L);

            // Assert
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // Arrange
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                likeApplicationService.addLike(1L, 99L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeDomainService, never()).addLike(anyLong(), anyLong());
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class RemoveLike {

        @DisplayName("상품이 존재하고 좋아요가 있으면, likeCount가 감소한다.")
        @Test
        void decrementsLikeCount_whenLikeExists() {
            // Arrange
            Product product = Product.create(1L, "에어맥스", "운동화", 100_000L);
            product.incrementLikeCount();
            when(productRepository.findById(2L)).thenReturn(Optional.of(product));
            when(likeDomainService.removeLike(1L, 2L)).thenReturn(true);

            // Act
            likeApplicationService.removeLike(1L, 2L);

            // Assert
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("좋아요가 존재하지 않으면, likeCount가 변경되지 않는다.")
        @Test
        void doesNotChangeLikeCount_whenLikeDoesNotExist() {
            // Arrange
            Product product = Product.create(1L, "에어맥스", "운동화", 100_000L);
            when(productRepository.findById(2L)).thenReturn(Optional.of(product));
            when(likeDomainService.removeLike(1L, 2L)).thenReturn(false);

            // Act
            likeApplicationService.removeLike(1L, 2L);

            // Assert
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // Arrange
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                likeApplicationService.removeLike(1L, 99L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeDomainService, never()).removeLike(anyLong(), anyLong());
        }
    }
}
