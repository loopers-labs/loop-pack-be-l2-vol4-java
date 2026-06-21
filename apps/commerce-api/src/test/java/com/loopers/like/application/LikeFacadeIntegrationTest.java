package com.loopers.like.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.like.infrastructure.LikeJpaRepository;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LikeFacadeIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 추가할 때,")
    @Nested
    class AddLike {

        @DisplayName("정상 요청이면, DB에 저장되고 LikeInfo를 반환하며 likeCount가 증가한다.")
        @Test
        void returnsLikeInfo_whenRequestIsValid() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));

            // act
            LikeInfo result = likeFacade.addLike(1L, product.getId());

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(1L),
                () -> assertThat(result.productId()).isEqualTo(product.getId())
            );
            ProductModel updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("이미 좋아요한 상품이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyLiked() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            likeFacade.addLike(1L, product.getId());

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                likeFacade.addLike(1L, product.getId())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("존재하지 않는 productId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                likeFacade.addLike(1L, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class CancelLike {

        @DisplayName("좋아요한 상품이면, DB에서 삭제되고 likeCount가 감소한다.")
        @Test
        void deletesLike_whenLikeExists() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            likeFacade.addLike(1L, product.getId());

            // act
            likeFacade.cancelLike(1L, product.getId());

            // assert
            assertThat(likeJpaRepository.findByUserIdAndProductId(1L, product.getId())).isEmpty();
            ProductModel updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getLikeCount()).isEqualTo(0L);
        }

        @DisplayName("좋아요하지 않은 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLikeNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                likeFacade.cancelLike(1L, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요한 상품 목록을 조회할 때,")
    @Nested
    class GetLikedProducts {

        @DisplayName("좋아요한 상품이 있으면, ProductInfo 목록을 반환한다.")
        @Test
        void returnsProductInfoList_whenLikedProductsExist() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            likeFacade.addLike(1L, product.getId());

            // act
            List<ProductInfo> result = likeFacade.getLikedProducts(1L);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).id()).isEqualTo(product.getId()),
                () -> assertThat(result.get(0).name()).isEqualTo("에어맥스")
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikedProducts() {
            // act
            List<ProductInfo> result = likeFacade.getLikedProducts(1L);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("브랜드가 있는 상품을 좋아요했을 때, brandName이 채워진 ProductInfo를 반환한다.")
        @Test
        void returnsBrandName_whenLikedProductHasBrand() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, brand.getId()));
            likeFacade.addLike(1L, product.getId());

            // act
            List<ProductInfo> result = likeFacade.getLikedProducts(1L);

            // assert
            assertThat(result.get(0).brandName()).isEqualTo("나이키");
        }

        @DisplayName("brandId가 null인 상품(노브랜드)을 좋아요했을 때, brandName이 null인 ProductInfo를 반환한다.")
        @Test
        void returnsNullBrandName_whenLikedProductHasNoBrand() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("기본 티셔츠", "무브랜드 상품", 10000L, null));
            likeFacade.addLike(1L, product.getId());

            // act
            List<ProductInfo> result = likeFacade.getLikedProducts(1L);

            // assert
            assertThat(result.get(0).brandName()).isNull();
        }
    }
}
