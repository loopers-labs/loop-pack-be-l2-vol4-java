package com.loopers.product.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.like.application.LikeFacade;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.SortCondition;
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
class ProductFacadeIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private LikeFacade likeFacade;

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

    @DisplayName("상품을 등록할 때,")
    @Nested
    class CreateProduct {

        @DisplayName("brandId 없이 정상 요청이면, DB에 저장되고 ProductInfo를 반환한다.")
        @Test
        void returnsProductInfo_whenBrandIdIsNull() {
            // act
            ProductInfo result = productFacade.createProduct("에어맥스", "나이키 운동화", 150000L, 100, null);

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("에어맥스"),
                () -> assertThat(result.brandId()).isNull()
            );
        }

        @DisplayName("존재하는 brandId로 정상 요청이면, DB에 저장되고 ProductInfo를 반환한다.")
        @Test
        void returnsProductInfo_whenBrandExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));

            // act
            ProductInfo result = productFacade.createProduct("에어맥스", "나이키 운동화", 150000L, 100, brand.getId());

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("에어맥스"),
                () -> assertThat(result.brandId()).isEqualTo(brand.getId())
            );
        }

        @DisplayName("존재하지 않는 brandId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                productFacade.createProduct("에어맥스", "나이키 운동화", 150000L, 100, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID이면, ProductInfo를 반환한다.")
        @Test
        void returnsProductInfo_whenProductExists() {
            // arrange
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));

            // act
            ProductInfo result = productFacade.getProduct(saved.getId());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.getId()),
                () -> assertThat(result.name()).isEqualTo("에어맥스"),
                () -> assertThat(result.price()).isEqualTo(150000L),
                () -> assertThat(result.likeCount()).isEqualTo(0L),
                () -> assertThat(result.brandName()).isNull()
            );
        }

        @DisplayName("브랜드가 있는 상품 조회 시, ProductInfo에 brandName과 likeCount가 포함된다.")
        @Test
        void returnsBrandNameAndLikeCount_whenProductHasBrand() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, brand.getId()));

            // act
            ProductInfo result = productFacade.getProduct(saved.getId());

            // assert
            assertAll(
                () -> assertThat(result.brandName()).isEqualTo("나이키"),
                () -> assertThat(result.likeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                productFacade.getProduct(999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 상품 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsDeleted() {
            // arrange
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            saved.delete();
            productJpaRepository.save(saved);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                productFacade.getProduct(saved.getId())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProducts {

        @DisplayName("상품이 있으면, ProductInfo 목록을 반환한다.")
        @Test
        void returnsProductInfoList_whenProductsExist() {
            // arrange
            productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, 50, null));

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.LATEST, null, 0, 20);

            // assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("brandId 필터를 적용하면 해당 브랜드 상품만 반환된다.")
        @Test
        void returnsFilteredList_whenBrandIdIsProvided() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, brand.getId()));
            productJpaRepository.save(new ProductModel("노브랜드상품", "브랜드 없음", 50000L, 10, null));

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.LATEST, brand.getId(), 0, 20);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).name()).isEqualTo("에어맥스"),
                () -> assertThat(result.get(0).brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("삭제된 상품은 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedProducts_fromList() {
            // arrange
            productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            ProductModel deleted = productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, 50, null));
            deleted.delete();
            productJpaRepository.save(deleted);

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.LATEST, null, 0, 20);

            // assert
            assertThat(result).hasSize(1);
        }

        @DisplayName("LATEST 정렬이면 최신 등록 상품이 앞에 온다.")
        @Test
        void returnsLatestFirst_whenSortIsLatest() {
            // arrange
            ProductModel first = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            ProductModel second = productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, 50, null));

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.LATEST, null, 0, 20);

            // assert — 나중에 저장된 조던1이 먼저 온다
            assertThat(result.get(0).id()).isEqualTo(second.getId());
        }

        @DisplayName("PRICE_ASC 정렬이면 가격 낮은 순으로 반환된다.")
        @Test
        void returnsCheapestFirst_whenSortIsPriceAsc() {
            // arrange
            productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, 50, null));
            productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.PRICE_ASC, null, 0, 20);

            // assert
            assertThat(result.get(0).price()).isEqualTo(150000L);
        }

        @DisplayName("LIKES_DESC 정렬이면 좋아요 많은 순으로 반환된다.")
        @Test
        void returnsMostLikedFirst_whenSortIsLikesDesc() {
            // arrange
            ProductModel popular = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            productJpaRepository.save(new ProductModel("조던1", "나이키 농구화", 200000L, 50, null));
            // [fix] @Modifying 직접 호출은 트랜잭션 필요 → LikeFacade.addLike()로 대체
            likeFacade.addLike(1L, popular.getId());

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.LIKES_DESC, null, 0, 20);

            // assert
            assertThat(result.get(0).id()).isEqualTo(popular.getId());
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class UpdateProduct {

        @DisplayName("정상 요청이면, 수정된 ProductInfo를 반환한다.")
        @Test
        void returnsUpdatedProductInfo_whenRequestIsValid() {
            // arrange
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));

            // act
            ProductInfo result = productFacade.updateProduct(saved.getId(), "조던1", "나이키 농구화", 200000L, 50);

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.getId()),
                () -> assertThat(result.name()).isEqualTo("조던1"),
                () -> assertThat(result.description()).isEqualTo("나이키 농구화"),
                () -> assertThat(result.price()).isEqualTo(200000L),
                () -> assertThat(result.stock()).isEqualTo(50)
            );
        }

        @DisplayName("존재하지 않는 상품 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                productFacade.updateProduct(999L, "조던1", "나이키 농구화", 200000L, 50)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @DisplayName("정상 요청이면, 상품이 소프트 딜리트된다.")
        @Test
        void softDeletesProduct_whenRequestIsValid() {
            // arrange
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));

            // act
            productFacade.deleteProduct(saved.getId());

            // assert — 삭제 후 조회 시 NOT_FOUND
            CoreException exception = assertThrows(CoreException.class, () ->
                productFacade.getProduct(saved.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                productFacade.deleteProduct(999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
