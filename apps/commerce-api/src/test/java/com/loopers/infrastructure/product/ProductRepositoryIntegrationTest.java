package com.loopers.infrastructure.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.projection.ProductAdminView;
import com.loopers.domain.product.projection.ProductDetail;
import com.loopers.domain.product.projection.ProductSummary;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel createProduct(String name) {
        return ProductModel.builder()
            .brandId(1L)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build();
    }

    private BrandModel saveBrand(String name) {
        return brandJpaRepository.save(BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build());
    }

    private ProductModel saveProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(price)
            .rawStock(stock)
            .build());
    }

    private void saveLike(Long userId, Long productId) {
        likeJpaRepository.save(LikeModel.builder()
            .userId(userId)
            .productId(productId)
            .build());
        productJpaRepository.incrementLikeCount(productId);
    }

    @DisplayName("상품을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장하면 식별자가 부여되고 모든 필드가 보존된 채 조회된다.")
        @Test
        void assignsId_andPreservesFields() {
            // arrange & act
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
            assertAll(
                () -> assertThat(savedProduct.getId()).isNotNull(),
                () -> assertThat(reloadedProduct.getBrandId()).isEqualTo(1L),
                () -> assertThat(reloadedProduct.getName().value()).isEqualTo("감성 가디건"),
                () -> assertThat(reloadedProduct.getDescription()).isEqualTo("포근한 감성 가디건"),
                () -> assertThat(reloadedProduct.getPrice().value()).isEqualTo(39_000),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(50)
            );
        }
    }

    @DisplayName("활성 상품을 식별자로 조회할 때,")
    @Nested
    class GetActiveById {

        @DisplayName("삭제되지 않은 상품이면 해당 상품을 반환한다.")
        @Test
        void returnsActiveProduct() {
            // arrange
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));

            // act
            ProductModel foundProduct = productRepository.getActiveById(savedProduct.getId());

            // assert
            assertThat(foundProduct.getId()).isEqualTo(savedProduct.getId());
        }

        @DisplayName("이미 삭제된 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsDeleted() {
            // arrange
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));
            savedProduct.delete();
            productJpaRepository.saveAndFlush(savedProduct);

            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveById(savedProduct.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 식별자면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveById(99999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("활성 상품을 식별자로 탐색할 때,")
    @Nested
    class FindActiveById {

        @DisplayName("삭제되지 않은 상품이면 해당 상품을 담은 Optional을 반환한다.")
        @Test
        void returnsPresent_whenProductIsActive() {
            // arrange
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));

            // act & assert
            assertThat(productRepository.findActiveById(savedProduct.getId())).isPresent();
        }

        @DisplayName("이미 삭제된 상품이면 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenProductIsDeleted() {
            // arrange
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));
            savedProduct.delete();
            productJpaRepository.saveAndFlush(savedProduct);

            // act & assert
            assertThat(productRepository.findActiveById(savedProduct.getId())).isEmpty();
        }

        @DisplayName("존재하지 않는 식별자면 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenProductIsAbsent() {
            // act & assert
            assertThat(productRepository.findActiveById(99999L)).isEmpty();
        }
    }

    @DisplayName("상품 목록 요약을 페이지로 조회할 때,")
    @Nested
    class FindActiveSummaries {

        @DisplayName("삭제된 상품과 브랜드 삭제로 함께 삭제된 상품을 제외하고 브랜드명과 함께 조회한다.")
        @Test
        void excludesDeletedProductAndDeletedBrandProduct() {
            // arrange
            BrandModel activeBrand = saveBrand("활성 브랜드");
            BrandModel deletedBrand = saveBrand("삭제 브랜드");
            ProductModel activeProduct = saveProduct(activeBrand.getId(), "활성 상품", 10_000, 5);
            ProductModel deletedProduct = saveProduct(activeBrand.getId(), "삭제 상품", 10_000, 5);
            deletedProduct.delete();
            productJpaRepository.saveAndFlush(deletedProduct);
            ProductModel deletedBrandProduct = saveProduct(deletedBrand.getId(), "삭제 브랜드 상품", 10_000, 5);
            deletedBrand.delete();
            brandJpaRepository.saveAndFlush(deletedBrand);
            deletedBrandProduct.delete();
            productJpaRepository.saveAndFlush(deletedBrandProduct);

            // act
            Page<ProductSummary> summaries = productRepository.findActiveSummaries(null, ProductSortType.LATEST, 0, 10);

            // assert
            assertAll(
                () -> assertThat(summaries.getTotalElements()).isEqualTo(1),
                () -> assertThat(summaries.getContent())
                    .extracting(ProductSummary::productId)
                    .containsExactly(activeProduct.getId()),
                () -> assertThat(summaries.getContent().get(0).brandName()).isEqualTo("활성 브랜드")
            );
        }

        @DisplayName("brandId 필터를 지정하면 해당 브랜드의 활성 상품만 조회한다.")
        @Test
        void filtersByBrandId() {
            // arrange
            BrandModel brandA = saveBrand("브랜드 A");
            BrandModel brandB = saveBrand("브랜드 B");
            ProductModel productA = saveProduct(brandA.getId(), "A 상품", 10_000, 5);
            saveProduct(brandB.getId(), "B 상품", 10_000, 5);

            // act
            Page<ProductSummary> summaries = productRepository.findActiveSummaries(brandA.getId(), ProductSortType.LATEST, 0, 10);

            // assert
            assertAll(
                () -> assertThat(summaries.getTotalElements()).isEqualTo(1),
                () -> assertThat(summaries.getContent())
                    .extracting(ProductSummary::productId)
                    .containsExactly(productA.getId())
            );
        }

        @DisplayName("존재하지 않는 brandId로 필터하면 빈 페이지를 반환한다.")
        @Test
        void returnsEmpty_whenBrandIdIsAbsent() {
            // arrange
            BrandModel brand = saveBrand("브랜드");
            saveProduct(brand.getId(), "상품", 10_000, 5);

            // act
            Page<ProductSummary> summaries = productRepository.findActiveSummaries(99999L, ProductSortType.LATEST, 0, 10);

            // assert
            assertThat(summaries.getTotalElements()).isEqualTo(0);
        }

        @DisplayName("최신 등록 순으로 조회하면 등록 시각 내림차순(가장 최근 등록이 먼저)으로 정렬된다.")
        @Test
        void sortsByLatest() {
            // arrange
            BrandModel brand = saveBrand("브랜드");
            ProductModel first = saveProduct(brand.getId(), "상품1", 10_000, 5);
            ProductModel second = saveProduct(brand.getId(), "상품2", 10_000, 5);
            ProductModel third = saveProduct(brand.getId(), "상품3", 10_000, 5);

            // act
            Page<ProductSummary> summaries = productRepository.findActiveSummaries(null, ProductSortType.LATEST, 0, 10);

            // assert
            assertThat(summaries.getContent())
                .extracting(ProductSummary::productId)
                .containsExactly(third.getId(), second.getId(), first.getId());
        }

        @DisplayName("가격 오름차순으로 조회하면 가격이 낮은 순으로 정렬된다.")
        @Test
        void sortsByPriceAsc() {
            // arrange
            BrandModel brand = saveBrand("브랜드");
            saveProduct(brand.getId(), "비싼 상품", 30_000, 5);
            saveProduct(brand.getId(), "싼 상품", 10_000, 5);
            saveProduct(brand.getId(), "중간 상품", 20_000, 5);

            // act
            Page<ProductSummary> summaries = productRepository.findActiveSummaries(null, ProductSortType.PRICE_ASC, 0, 10);

            // assert
            assertThat(summaries.getContent())
                .extracting(ProductSummary::price)
                .containsExactly(10_000, 20_000, 30_000);
        }

        @DisplayName("좋아요 많은 순으로 조회하면 좋아요 수 내림차순으로 정렬된다.")
        @Test
        void sortsByLikesDesc() {
            // arrange
            BrandModel brand = saveBrand("브랜드");
            ProductModel mostLiked = saveProduct(brand.getId(), "인기 상품", 10_000, 5);
            ProductModel leastLiked = saveProduct(brand.getId(), "비인기 상품", 10_000, 5);
            ProductModel middleLiked = saveProduct(brand.getId(), "중간 상품", 10_000, 5);
            saveLike(1L, mostLiked.getId());
            saveLike(2L, mostLiked.getId());
            saveLike(3L, mostLiked.getId());
            saveLike(1L, middleLiked.getId());

            // act
            Page<ProductSummary> summaries = productRepository.findActiveSummaries(null, ProductSortType.LIKES_DESC, 0, 10);

            // assert
            assertThat(summaries.getContent())
                .extracting(ProductSummary::productId)
                .containsExactly(mostLiked.getId(), middleLiked.getId(), leastLiked.getId());
        }

        @DisplayName("상품별 좋아요 수를 정확히 집계하며 좋아요가 없으면 0이다.")
        @Test
        void aggregatesLikeCount() {
            // arrange
            BrandModel brand = saveBrand("브랜드");
            ProductModel likedProduct = saveProduct(brand.getId(), "좋아요 상품", 10_000, 5);
            ProductModel notLikedProduct = saveProduct(brand.getId(), "노 좋아요 상품", 10_000, 5);
            saveLike(1L, likedProduct.getId());
            saveLike(2L, likedProduct.getId());

            // act
            Page<ProductSummary> summaries = productRepository.findActiveSummaries(null, ProductSortType.LIKES_DESC, 0, 10);

            // assert
            assertAll(
                () -> assertThat(summaries.getContent())
                    .filteredOn(summary -> summary.productId().equals(likedProduct.getId()))
                    .extracting(ProductSummary::likeCount)
                    .containsExactly(2),
                () -> assertThat(summaries.getContent())
                    .filteredOn(summary -> summary.productId().equals(notLikedProduct.getId()))
                    .extracting(ProductSummary::likeCount)
                    .containsExactly(0)
            );
        }

        @DisplayName("페이지 크기와 오프셋대로 페이징하고 총 개수를 보존한다.")
        @Test
        void appliesPaging() {
            // arrange
            BrandModel brand = saveBrand("브랜드");
            saveProduct(brand.getId(), "상품1", 10_000, 5);
            saveProduct(brand.getId(), "상품2", 10_000, 5);
            saveProduct(brand.getId(), "상품3", 10_000, 5);

            // act
            Page<ProductSummary> firstPage = productRepository.findActiveSummaries(null, ProductSortType.LATEST, 0, 2);

            // assert
            assertAll(
                () -> assertThat(firstPage.getTotalElements()).isEqualTo(3),
                () -> assertThat(firstPage.getTotalPages()).isEqualTo(2),
                () -> assertThat(firstPage.getContent()).hasSize(2)
            );
        }
    }

    @DisplayName("상품 상세를 식별자로 조회할 때,")
    @Nested
    class GetActiveDetailById {

        @DisplayName("활성 상품이면 브랜드명과 좋아요 수를 포함한 상세를 반환한다.")
        @Test
        void returnsDetail_whenProductIsActive() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 5);
            saveLike(1L, product.getId());
            saveLike(2L, product.getId());

            // act
            ProductDetail detail = productRepository.getActiveDetailById(product.getId());

            // assert
            assertAll(
                () -> assertThat(detail.productId()).isEqualTo(product.getId()),
                () -> assertThat(detail.brandName()).isEqualTo("감성 브랜드"),
                () -> assertThat(detail.likeCount()).isEqualTo(2)
            );
        }

        @DisplayName("좋아요가 없으면 좋아요 수가 0이다.")
        @Test
        void returnsZeroLikeCount_whenNoLikes() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 5);

            // act
            ProductDetail detail = productRepository.getActiveDetailById(product.getId());

            // assert
            assertThat(detail.likeCount()).isEqualTo(0);
        }

        @DisplayName("삭제된 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsDeleted() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 5);
            product.delete();
            productJpaRepository.saveAndFlush(product);

            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveDetailById(product.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("브랜드가 삭제되면 그 상품도 함께 삭제되어 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIsDeleted() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 5);
            brand.delete();
            brandJpaRepository.saveAndFlush(brand);
            product.delete();
            productJpaRepository.saveAndFlush(product);

            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveDetailById(product.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 식별자면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveDetailById(99999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("관리자 상품 목록을 페이지로 조회할 때,")
    @Nested
    class FindActiveAdminViews {

        @DisplayName("삭제된 상품을 제외하고 등록 시각 내림차순으로 브랜드명과 함께 페이징한다.")
        @Test
        void returnsAdminViews_excludingDeleted_sortedByCreatedAtDesc() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            saveProduct(brand.getId(), "상품1", 10_000, 5);
            saveProduct(brand.getId(), "상품2", 10_000, 7);
            ProductModel deleted = saveProduct(brand.getId(), "삭제 상품", 10_000, 5);
            deleted.delete();
            productJpaRepository.saveAndFlush(deleted);

            // act
            Page<ProductAdminView> views = productRepository.findActiveAdminViews(null, 0, 10);

            // assert
            assertAll(
                () -> assertThat(views.getTotalElements()).isEqualTo(2),
                () -> assertThat(views.getContent())
                    .extracting(ProductAdminView::createdAt)
                    .isSortedAccordingTo(Comparator.reverseOrder()),
                () -> assertThat(views.getContent().get(0).brandName()).isEqualTo("감성 브랜드")
            );
        }

        @DisplayName("brandId 필터를 지정하면 해당 브랜드의 활성 상품만 조회한다.")
        @Test
        void filtersByBrandId() {
            // arrange
            BrandModel brandA = saveBrand("브랜드 A");
            BrandModel brandB = saveBrand("브랜드 B");
            ProductModel productA = saveProduct(brandA.getId(), "A 상품", 10_000, 5);
            saveProduct(brandB.getId(), "B 상품", 10_000, 5);

            // act
            Page<ProductAdminView> views = productRepository.findActiveAdminViews(brandA.getId(), 0, 10);

            // assert
            assertAll(
                () -> assertThat(views.getTotalElements()).isEqualTo(1),
                () -> assertThat(views.getContent())
                    .extracting(ProductAdminView::productId)
                    .containsExactly(productA.getId())
            );
        }

        @DisplayName("존재하지 않는 brandId로 필터하면 빈 페이지를 반환한다.")
        @Test
        void returnsEmpty_whenBrandIdIsAbsent() {
            // arrange
            BrandModel brand = saveBrand("브랜드");
            saveProduct(brand.getId(), "상품", 10_000, 5);

            // act
            Page<ProductAdminView> views = productRepository.findActiveAdminViews(99999L, 0, 10);

            // assert
            assertThat(views.getTotalElements()).isEqualTo(0);
        }

        @DisplayName("정확한 재고 수량을 노출한다.")
        @Test
        void exposesExactStock() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            saveProduct(brand.getId(), "상품", 10_000, 7);

            // act
            Page<ProductAdminView> views = productRepository.findActiveAdminViews(null, 0, 10);

            // assert
            assertThat(views.getContent().get(0).stock()).isEqualTo(7);
        }
    }

    @DisplayName("관리자 상품 상세를 식별자로 조회할 때,")
    @Nested
    class GetActiveAdminViewById {

        @DisplayName("활성 상품이면 브랜드명·정확 재고·시각을 포함한 상세를 반환한다.")
        @Test
        void returnsView_whenProductIsActive() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);

            // act
            ProductAdminView view = productRepository.getActiveAdminViewById(product.getId());

            // assert
            assertAll(
                () -> assertThat(view.productId()).isEqualTo(product.getId()),
                () -> assertThat(view.brandName()).isEqualTo("감성 브랜드"),
                () -> assertThat(view.stock()).isEqualTo(50)
            );
        }

        @DisplayName("삭제된 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsDeleted() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            product.delete();
            productJpaRepository.saveAndFlush(product);

            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveAdminViewById(product.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 식별자면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // act & assert
            assertThatThrownBy(() -> productRepository.getActiveAdminViewById(99999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

}
