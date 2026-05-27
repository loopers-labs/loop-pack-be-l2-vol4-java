package com.loopers.infrastructure.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.projection.ProductSummary;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class LikeRepositoryIntegrationTest {

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private LikeModel createLike(Long userId, Long productId) {
        return LikeModel.builder()
            .userId(userId)
            .productId(productId)
            .build();
    }

    private BrandModel saveBrand(String name) {
        return brandJpaRepository.save(BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build());
    }

    private ProductModel saveProduct(Long brandId, String name) {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build());
    }

    @DisplayName("좋아요를 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장하면 식별자가 부여되고 userId·productId가 보존된다.")
        @Test
        void assignsId_andPreservesFields() {
            // arrange & act
            LikeModel savedLike = likeRepository.save(createLike(1L, 1L));

            // assert
            LikeModel reloadedLike = likeJpaRepository.findById(savedLike.getId()).orElseThrow();
            assertAll(
                () -> assertThat(savedLike.getId()).isNotNull(),
                () -> assertThat(reloadedLike.getUserId()).isEqualTo(1L),
                () -> assertThat(reloadedLike.getProductId()).isEqualTo(1L)
            );
        }
    }

    @DisplayName("(userId, productId) 조합 존재 여부를 조회할 때,")
    @Nested
    class ExistsByUserIdAndProductId {

        @DisplayName("저장된 조합이면 true, 없으면 false를 반환한다.")
        @Test
        void returnsTrueForExisting_andFalseOtherwise() {
            // arrange
            likeRepository.save(createLike(1L, 1L));

            // act & assert
            assertAll(
                () -> assertThat(likeRepository.existsByUserIdAndProductId(1L, 1L)).isTrue(),
                () -> assertThat(likeRepository.existsByUserIdAndProductId(1L, 2L)).isFalse(),
                () -> assertThat(likeRepository.existsByUserIdAndProductId(2L, 1L)).isFalse()
            );
        }
    }

    @DisplayName("동일한 (userId, productId)로 중복 저장을 시도할 때,")
    @Nested
    class UniqueConstraint {

        @DisplayName("동일한 (userId, productId) 조합을 다시 저장하면 저장이 거부된다.")
        @Test
        void throwsDataIntegrityViolation_whenDuplicateSave() {
            // arrange
            likeRepository.save(createLike(1L, 1L));

            // act & assert
            assertThatThrownBy(() -> likeRepository.save(createLike(1L, 1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @DisplayName("좋아요한 상품 목록을 페이지로 조회할 때,")
    @Nested
    class FindLikedProductSummaries {

        private static final Long USER_ID = 1L;

        @DisplayName("인증 회원이 좋아요한 활성 상품만 최신 좋아요 순으로 반환한다.")
        @Test
        void returnsLikedActiveProducts_sortedByLatestLike() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel firstLiked = saveProduct(brand.getId(), "먼저 좋아요한 상품");
            ProductModel lastLiked = saveProduct(brand.getId(), "나중에 좋아요한 상품");
            likeRepository.save(createLike(USER_ID, firstLiked.getId()));
            likeRepository.save(createLike(USER_ID, lastLiked.getId()));

            // act
            Page<ProductSummary> summaries = likeRepository.findLikedProductSummaries(USER_ID, 0, 10);

            // assert
            assertThat(summaries.getContent())
                .extracting(ProductSummary::productId)
                .containsExactly(lastLiked.getId(), firstLiked.getId());
        }

        @DisplayName("좋아요한 뒤 삭제된 상품과 소속 브랜드가 삭제된 상품은 제외된다.")
        @Test
        void excludesDeletedProductAndDeletedBrandProduct() {
            // arrange
            BrandModel activeBrand = saveBrand("활성 브랜드");
            BrandModel deletedBrand = saveBrand("삭제 브랜드");
            ProductModel activeProduct = saveProduct(activeBrand.getId(), "활성 상품");
            ProductModel deletedProduct = saveProduct(activeBrand.getId(), "삭제 상품");
            ProductModel brandDeletedProduct = saveProduct(deletedBrand.getId(), "브랜드 삭제 상품");
            likeRepository.save(createLike(USER_ID, activeProduct.getId()));
            likeRepository.save(createLike(USER_ID, deletedProduct.getId()));
            likeRepository.save(createLike(USER_ID, brandDeletedProduct.getId()));
            deletedProduct.delete();
            productJpaRepository.saveAndFlush(deletedProduct);
            deletedBrand.delete();
            brandJpaRepository.saveAndFlush(deletedBrand);

            // act
            Page<ProductSummary> summaries = likeRepository.findLikedProductSummaries(USER_ID, 0, 10);

            // assert
            assertAll(
                () -> assertThat(summaries.getTotalElements()).isEqualTo(1),
                () -> assertThat(summaries.getContent())
                    .extracting(ProductSummary::productId)
                    .containsExactly(activeProduct.getId())
            );
        }

        @DisplayName("좋아요 수는 상품의 전체 좋아요를 집계하며 타 회원의 좋아요한 상품은 목록에 포함되지 않는다.")
        @Test
        void aggregatesLikeCount_andExcludesOtherUsersLikes() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel myProduct = saveProduct(brand.getId(), "내가 좋아요한 상품");
            ProductModel othersProduct = saveProduct(brand.getId(), "타인이 좋아요한 상품");
            likeRepository.save(createLike(USER_ID, myProduct.getId()));
            likeRepository.save(createLike(2L, myProduct.getId()));
            likeRepository.save(createLike(2L, othersProduct.getId()));

            // act
            Page<ProductSummary> summaries = likeRepository.findLikedProductSummaries(USER_ID, 0, 10);

            // assert
            assertAll(
                () -> assertThat(summaries.getContent())
                    .extracting(ProductSummary::productId)
                    .containsExactly(myProduct.getId()),
                () -> assertThat(summaries.getContent().get(0).likeCount()).isEqualTo(2)
            );
        }

        @DisplayName("페이지 크기와 오프셋대로 페이징하고 총 개수를 보존한다.")
        @Test
        void appliesPaging() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product1 = saveProduct(brand.getId(), "상품1");
            ProductModel product2 = saveProduct(brand.getId(), "상품2");
            ProductModel product3 = saveProduct(brand.getId(), "상품3");
            likeRepository.save(createLike(USER_ID, product1.getId()));
            likeRepository.save(createLike(USER_ID, product2.getId()));
            likeRepository.save(createLike(USER_ID, product3.getId()));

            // act
            Page<ProductSummary> firstPage = likeRepository.findLikedProductSummaries(USER_ID, 0, 2);

            // assert
            assertAll(
                () -> assertThat(firstPage.getTotalElements()).isEqualTo(3),
                () -> assertThat(firstPage.getTotalPages()).isEqualTo(2),
                () -> assertThat(firstPage.getContent()).hasSize(2)
            );
        }
    }
}
