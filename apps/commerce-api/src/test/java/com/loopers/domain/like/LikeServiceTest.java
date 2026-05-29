package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final LikeService likeService = new LikeService(likeRepository, productRepository);

    private ProductModel product() {
        return new ProductModel(1L, "에어맥스", "운동화", 1000L, 10);
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Like {

        @DisplayName("아직 좋아요하지 않았으면, Like 를 저장하고 상품의 좋아요 수를 증가시킨다.")
        @Test
        void savesAndIncrements_whenNotLikedYet() {
            // arrange
            ProductModel product = product();
            when(likeRepository.existsBy(USER_ID, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product));

            // act
            likeService.like(USER_ID, PRODUCT_ID);

            // assert
            verify(likeRepository).save(any(LikeModel.class));
            verify(productRepository).save(product);
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("이미 좋아요한 경우, 아무 것도 하지 않는다. (멱등)")
        @Test
        void doesNothing_whenAlreadyLiked() {
            // arrange
            when(likeRepository.existsBy(USER_ID, PRODUCT_ID)).thenReturn(true);

            // act
            likeService.like(USER_ID, PRODUCT_ID);

            // assert
            verify(likeRepository, never()).save(any());
            verify(productRepository, never()).save(any());
        }

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생하고 Like 는 저장되지 않는다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            // arrange
            when(likeRepository.existsBy(USER_ID, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class, () -> likeService.like(USER_ID, PRODUCT_ID));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeRepository, never()).save(any());
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상태이면, Like 를 삭제하고 상품의 좋아요 수를 감소시킨다.")
        @Test
        void deletesAndDecrements_whenLiked() {
            // arrange
            ProductModel product = product();
            product.increaseLikeCount(); // likeCount = 1
            when(likeRepository.existsBy(USER_ID, PRODUCT_ID)).thenReturn(true);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product));

            // act
            likeService.unlike(USER_ID, PRODUCT_ID);

            // assert
            verify(likeRepository).deleteBy(USER_ID, PRODUCT_ID);
            verify(productRepository).save(product);
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요하지 않은 상태이면, 아무 것도 하지 않는다. (멱등)")
        @Test
        void doesNothing_whenNotLiked() {
            // arrange
            when(likeRepository.existsBy(USER_ID, PRODUCT_ID)).thenReturn(false);

            // act
            likeService.unlike(USER_ID, PRODUCT_ID);

            // assert
            verify(likeRepository, never()).deleteBy(any(), any());
            verify(productRepository, never()).save(any());
        }
    }
}
