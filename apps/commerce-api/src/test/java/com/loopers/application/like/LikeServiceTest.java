package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @InjectMocks
    private LikeService likeService;

    @Mock private LikeRepository likeRepository;
    @Mock private ProductRepository productRepository;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private ProductModel activeProduct;
    private ProductModel deletedProduct;

    @BeforeEach
    void setUp() {
        BrandModel brand = new BrandModel("Nike", "스포츠 브랜드");
        activeProduct = new ProductModel(brand, "나이키 에어맥스", 150_000);
        deletedProduct = new ProductModel(brand, "단종 상품", 100_000);
        deletedProduct.delete();
    }

    @DisplayName("like()를 호출할 때,")
    @Nested
    class Like {

        @DisplayName("존재하는 활성 상품에 좋아요 시 LikeModel이 저장된다.")
        @Test
        void savesLike_whenProductIsActiveAndNotYetLiked() {
            // arrange
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct));
            given(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);

            // act
            likeService.like(USER_ID, PRODUCT_ID);

            // assert
            then(likeRepository).should().save(any(LikeModel.class));
        }

        @DisplayName("이미 좋아요한 상품에 재요청 시 저장 없이 정상 처리된다 (멱등).")
        @Test
        void doesNotSave_whenAlreadyLiked() {
            // arrange
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(activeProduct));
            given(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(true);

            // act
            likeService.like(USER_ID, PRODUCT_ID);

            // assert
            then(likeRepository).should(never()).save(any());
        }

        @DisplayName("존재하지 않는 상품에 좋아요 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                likeService.like(USER_ID, PRODUCT_ID)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(likeRepository).should(never()).save(any());
        }

        @DisplayName("삭제된 상품에 좋아요 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIsDeleted() {
            // arrange
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(deletedProduct));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                likeService.like(USER_ID, PRODUCT_ID)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(likeRepository).should(never()).save(any());
        }
    }

    @DisplayName("unlike()를 호출할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 존재하면 삭제된다.")
        @Test
        void deletesLike_whenLikeExists() {
            // arrange
            given(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(true);

            // act
            likeService.unlike(USER_ID, PRODUCT_ID);

            // assert
            then(likeRepository).should().deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
        }

        @DisplayName("이미 취소된 상태에서 재요청 시 삭제 없이 정상 처리된다 (멱등).")
        @Test
        void doesNotDelete_whenLikeDoesNotExist() {
            // arrange
            given(likeRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);

            // act
            likeService.unlike(USER_ID, PRODUCT_ID);

            // assert
            then(likeRepository).should(never()).deleteByUserIdAndProductId(any(), any());
        }
    }

    @DisplayName("getUserLikes()를 호출할 때,")
    @Nested
    class GetUserLikes {

        @DisplayName("본인의 좋아요 목록 조회 시 활성 상품의 LikeInfo 목록이 반환된다.")
        @Test
        void returnsLikeInfoList_whenRequesterIsSameUser() {
            // arrange
            LikeModel like = new LikeModel(USER_ID, PRODUCT_ID);
            given(likeRepository.findAllByUserId(USER_ID)).willReturn(List.of(like));
            given(productRepository.findAllActiveByIds(List.of(PRODUCT_ID))).willReturn(List.of(activeProduct));

            // act
            List<LikeInfo> result = likeService.getUserLikes(USER_ID, USER_ID);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).productName()).isEqualTo("나이키 에어맥스");
            assertThat(result.get(0).brandName()).isEqualTo("Nike");
        }

        @DisplayName("삭제된 상품의 좋아요 항목은 목록에서 제외된다.")
        @Test
        void excludesDeletedProducts_fromLikesList() {
            // arrange
            Long deletedProductId = 20L;
            LikeModel likeForActive = new LikeModel(USER_ID, PRODUCT_ID);
            LikeModel likeForDeleted = new LikeModel(USER_ID, deletedProductId);
            given(likeRepository.findAllByUserId(USER_ID)).willReturn(List.of(likeForActive, likeForDeleted));
            // activeProduct만 반환 (deletedProductId는 findAllActiveByIds에서 제외됨)
            given(productRepository.findAllActiveByIds(List.of(PRODUCT_ID, deletedProductId)))
                .willReturn(List.of(activeProduct));

            // act
            List<LikeInfo> result = likeService.getUserLikes(USER_ID, USER_ID);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).productName()).isEqualTo("나이키 에어맥스");
        }

        @DisplayName("타인의 좋아요 목록 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenRequesterIsNotOwner() {
            // arrange
            Long otherUserId = 99L;

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                likeService.getUserLikes(USER_ID, otherUserId)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(likeRepository).should(never()).findAllByUserId(any());
        }
    }
}
