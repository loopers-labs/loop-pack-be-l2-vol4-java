package com.loopers.application.like;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class LikeFacadeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private LikeFacade likeFacade;

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class CreateLike {

        private final Long userId = 1L;
        private final Long productId = 1L;

        @DisplayName("회원과 상품이 활성 상태이고 좋아요가 없으면 저장한다.")
        @Test
        void savesLike_whenBothActiveAndNotYetLiked() {
            // arrange
            given(userRepository.getActiveById(userId)).willReturn(mock(UserModel.class));
            given(productRepository.getActiveById(productId)).willReturn(mock(ProductModel.class));
            given(likeRepository.existsByUserIdAndProductId(anyLong(), anyLong())).willReturn(false);
            given(likeRepository.save(any(LikeModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            likeFacade.createLike(userId, productId);

            // assert
            then(likeRepository).should().save(any(LikeModel.class));
        }

        @DisplayName("이미 좋아요한 상품이면 저장하지 않는다(멱등).")
        @Test
        void doesNotSave_whenAlreadyLiked() {
            // arrange
            given(userRepository.getActiveById(userId)).willReturn(mock(UserModel.class));
            given(productRepository.getActiveById(productId)).willReturn(mock(ProductModel.class));
            given(likeRepository.existsByUserIdAndProductId(anyLong(), anyLong())).willReturn(true);

            // act
            likeFacade.createLike(userId, productId);

            // assert
            then(likeRepository).should(never()).save(any(LikeModel.class));
        }

        @DisplayName("상품이 없거나 삭제된 경우 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // arrange
            given(userRepository.getActiveById(userId)).willReturn(mock(UserModel.class));
            given(productRepository.getActiveById(productId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> likeFacade.createLike(userId, productId))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(likeRepository).should(never()).save(any(LikeModel.class))
            );
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class DeleteLike {

        private final Long userId = 1L;
        private final Long productId = 1L;

        @DisplayName("회원과 상품이 활성 상태이면 deleteByUserIdAndProductId를 호출한다.")
        @Test
        void callsDelete_whenBothActive() {
            // arrange
            given(userRepository.getActiveById(userId)).willReturn(mock(UserModel.class));
            given(productRepository.getActiveById(productId)).willReturn(mock(ProductModel.class));

            // act
            likeFacade.deleteLike(userId, productId);

            // assert
            then(likeRepository).should().deleteByUserIdAndProductId(anyLong(), anyLong());
        }

        @DisplayName("상품이 없거나 삭제된 경우 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // arrange
            given(userRepository.getActiveById(userId)).willReturn(mock(UserModel.class));
            given(productRepository.getActiveById(productId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> likeFacade.deleteLike(userId, productId))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(likeRepository).should(never()).deleteByUserIdAndProductId(any(), any())
            );
        }
    }
}
