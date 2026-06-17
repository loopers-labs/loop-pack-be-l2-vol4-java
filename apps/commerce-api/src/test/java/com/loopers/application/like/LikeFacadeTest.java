package com.loopers.application.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.projection.ProductSummary;
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

        @DisplayName("회원과 상품이 활성 상태이고 좋아요가 없으면 저장하고 좋아요 수를 1 증가시킨다.")
        @Test
        void savesLike_andIncrementsLikeCount_whenBothActiveAndNotYetLiked() {
            // arrange
            UserModel user = mock(UserModel.class);
            ProductModel product = mock(ProductModel.class);
            given(user.getId()).willReturn(userId);
            given(product.getId()).willReturn(productId);
            given(userRepository.getActiveById(userId)).willReturn(user);
            given(productRepository.getActiveById(productId)).willReturn(product);
            given(likeRepository.existsByUserIdAndProductId(userId, productId)).willReturn(false);

            // act
            likeFacade.createLike(userId, productId);

            // assert
            assertAll(
                () -> then(likeRepository).should().save(any(LikeModel.class)),
                () -> then(productRepository).should().incrementLikeCount(productId)
            );
        }

        @DisplayName("이미 좋아요한 상품이면 저장하지 않고 좋아요 수도 증가시키지 않는다(멱등).")
        @Test
        void doesNotSaveNorIncrement_whenAlreadyLiked() {
            // arrange
            UserModel user = mock(UserModel.class);
            ProductModel product = mock(ProductModel.class);
            given(user.getId()).willReturn(userId);
            given(product.getId()).willReturn(productId);
            given(userRepository.getActiveById(userId)).willReturn(user);
            given(productRepository.getActiveById(productId)).willReturn(product);
            given(likeRepository.existsByUserIdAndProductId(userId, productId)).willReturn(true);

            // act
            likeFacade.createLike(userId, productId);

            // assert
            assertAll(
                () -> then(likeRepository).should(never()).save(any(LikeModel.class)),
                () -> then(productRepository).should(never()).incrementLikeCount(anyLong())
            );
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
                () -> then(likeRepository).should(never()).save(any(LikeModel.class)),
                () -> then(productRepository).should(never()).incrementLikeCount(anyLong())
            );
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class DeleteLike {

        private final Long userId = 1L;
        private final Long productId = 1L;

        @DisplayName("좋아요가 실제로 삭제되면 좋아요 수를 1 감소시킨다.")
        @Test
        void decrementsLikeCount_whenLikeIsRemoved() {
            // arrange
            UserModel user = mock(UserModel.class);
            ProductModel product = mock(ProductModel.class);
            given(user.getId()).willReturn(userId);
            given(product.getId()).willReturn(productId);
            given(userRepository.getActiveById(userId)).willReturn(user);
            given(productRepository.getActiveById(productId)).willReturn(product);
            given(likeRepository.deleteByUserIdAndProductId(userId, productId)).willReturn(1);

            // act
            likeFacade.deleteLike(userId, productId);

            // assert
            assertAll(
                () -> then(likeRepository).should().deleteByUserIdAndProductId(userId, productId),
                () -> then(productRepository).should().decrementLikeCount(productId)
            );
        }

        @DisplayName("삭제할 좋아요가 없으면 좋아요 수를 감소시키지 않는다(멱등).")
        @Test
        void doesNotDecrement_whenNothingDeleted() {
            // arrange
            UserModel user = mock(UserModel.class);
            ProductModel product = mock(ProductModel.class);
            given(user.getId()).willReturn(userId);
            given(product.getId()).willReturn(productId);
            given(userRepository.getActiveById(userId)).willReturn(user);
            given(productRepository.getActiveById(productId)).willReturn(product);
            given(likeRepository.deleteByUserIdAndProductId(userId, productId)).willReturn(0);

            // act
            likeFacade.deleteLike(userId, productId);

            // assert
            assertAll(
                () -> then(likeRepository).should().deleteByUserIdAndProductId(userId, productId),
                () -> then(productRepository).should(never()).decrementLikeCount(anyLong())
            );
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
                () -> then(likeRepository).should(never()).deleteByUserIdAndProductId(any(), any()),
                () -> then(productRepository).should(never()).decrementLikeCount(anyLong())
            );
        }
    }

    @DisplayName("좋아요한 상품 목록을 조회할 때,")
    @Nested
    class ReadLikedProducts {

        @DisplayName("경로 회원이 인증 회원과 일치하면 좋아요한 상품을 ProductSummaryInfo 페이지로 반환한다.")
        @Test
        void returnsSummaryInfoPage_whenPathUserMatchesAuthUser() {
            // arrange
            ProductSummary summary = new ProductSummary(1L, "감성 가디건", 1L, "감성 브랜드", 39_000, 5, 2);
            given(likeRepository.findLikedProductSummaries(1L, 0, 20)).willReturn(new PageImpl<>(List.of(summary)));

            // act
            Page<ProductSummaryInfo> result = likeFacade.readLikedProducts(1L, 1L, 0, 20);

            // assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(1),
                () -> assertThat(result.getContent().get(0).likeCount()).isEqualTo(2),
                () -> then(likeRepository).should().findLikedProductSummaries(1L, 0, 20)
            );
        }

        @DisplayName("경로 회원이 인증 회원과 다르면 조회 없이 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenPathUserDiffersFromAuthUser() {
            // act
            Page<ProductSummaryInfo> result = likeFacade.readLikedProducts(1L, 2L, 0, 20);

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(0L),
                () -> then(likeRepository).should(never()).findLikedProductSummaries(anyLong(), anyInt(), anyInt())
            );
        }
    }
}
