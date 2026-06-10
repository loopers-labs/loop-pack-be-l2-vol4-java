package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
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

class LikeFacadeTest {

    private static final String LOGIN_ID = "tester01";
    private static final Long PRODUCT_ID = 100L;

    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LikeFacade likeFacade = new LikeFacade(likeRepository, productRepository, userRepository);

    private void givenUser(long id) {
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn(id);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
    }

    private ProductModel product() {
        return new ProductModel(1L, "에어맥스", "운동화", 1000L, 10);
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Like {

        @DisplayName("아직 좋아요하지 않았으면, Like 저장 후 상품 좋아요 수를 증가시킨다.")
        @Test
        void savesAndIncrements() {
            // arrange
            ProductModel product = product();
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product));

            // act
            likeFacade.like(LOGIN_ID, PRODUCT_ID);

            // assert
            verify(likeRepository).save(any(LikeModel.class));
            verify(productRepository).save(product);
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("이미 좋아요한 경우, 아무 것도 하지 않는다. (멱등)")
        @Test
        void idempotent() {
            // arrange
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(true);

            // act
            likeFacade.like(LOGIN_ID, PRODUCT_ID);

            // assert
            verify(likeRepository, never()).save(any());
            verify(productRepository, never()).save(any());
        }

        @DisplayName("상품이 없으면 NOT_FOUND 이고 Like 는 저장되지 않는다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            // arrange
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class, () -> likeFacade.like(LOGIN_ID, PRODUCT_ID));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeRepository, never()).save(any());
        }

        @DisplayName("유저가 없으면 NOT_FOUND 이고 좋아요 처리도 하지 않는다.")
        @Test
        void throwsNotFound_whenUserMissing() {
            // arrange
            when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class, () -> likeFacade.like(LOGIN_ID, PRODUCT_ID));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(likeRepository, never()).existsBy(any(), any());
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상태면, Like 삭제 후 상품 좋아요 수를 감소시킨다.")
        @Test
        void deletesAndDecrements() {
            // arrange
            ProductModel product = product();
            product.increaseLikeCount(); // likeCount = 1
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(true);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product));

            // act
            likeFacade.unlike(LOGIN_ID, PRODUCT_ID);

            // assert
            verify(likeRepository).deleteBy(7L, PRODUCT_ID);
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요하지 않은 상태면, 아무 것도 하지 않는다. (멱등)")
        @Test
        void idempotent() {
            // arrange
            givenUser(7L);
            when(likeRepository.existsBy(7L, PRODUCT_ID)).thenReturn(false);

            // act
            likeFacade.unlike(LOGIN_ID, PRODUCT_ID);

            // assert
            verify(likeRepository, never()).deleteBy(any(), any());
        }
    }
}
