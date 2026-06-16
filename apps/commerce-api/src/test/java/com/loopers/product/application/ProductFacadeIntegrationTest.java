package com.loopers.product.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.like.application.LikeFacade;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.SortCondition;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.infrastructure.StockJpaRepository;
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
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel savedProduct(String name, int stock) {
        ProductModel product = productJpaRepository.save(new ProductModel(name, "나이키 운동화", 150000L, null));
        stockJpaRepository.save(new StockModel(product.getId(), stock));
        return product;
    }

    @DisplayName("상품을 등록할 때,")
    @Nested
    class CreateProduct {

        @DisplayName("brandId 없이 정상 요청이면, DB에 저장되고 ProductInfo와 재고가 함께 생성된다.")
        @Test
        void returnsProductInfo_andCreatesStock_whenBrandIdIsNull() {
            // act
            ProductInfo result = productFacade.createProduct("에어맥스", "나이키 운동화", 150000L, 100, null);

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("에어맥스"),
                () -> assertThat(result.stock()).isEqualTo(100),
                () -> assertThat(result.brandId()).isNull()
            );

            StockModel stock = stockJpaRepository.findByProductId(result.id()).orElseThrow();
            assertThat(stock.getTotalStock()).isEqualTo(100);
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
            ProductModel saved = savedProduct("에어맥스", 100);

            // act
            ProductInfo result = productFacade.getProduct(saved.getId());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.getId()),
                () -> assertThat(result.name()).isEqualTo("에어맥스"),
                () -> assertThat(result.price()).isEqualTo(150000L),
                () -> assertThat(result.stock()).isEqualTo(100),
                () -> assertThat(result.likeCount()).isEqualTo(0L),
                () -> assertThat(result.brandName()).isNull()
            );
        }

        @DisplayName("브랜드가 있는 상품 조회 시, ProductInfo에 brandName이 포함된다.")
        @Test
        void returnsBrandName_whenProductHasBrand() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel saved = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, brand.getId()));
            stockJpaRepository.save(new StockModel(saved.getId(), 100));

            // act
            ProductInfo result = productFacade.getProduct(saved.getId());

            // assert
            assertThat(result.brandName()).isEqualTo("나이키");
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
            ProductModel saved = savedProduct("에어맥스", 100);
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
            savedProduct("에어맥스", 100);
            savedProduct("조던1", 50);

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.LATEST, null, false, 0, 20);

            // assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("삭제된 상품은 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedProducts_fromList() {
            // arrange
            savedProduct("에어맥스", 100);
            ProductModel deleted = savedProduct("조던1", 50);
            deleted.delete();
            productJpaRepository.save(deleted);

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.LATEST, null, false, 0, 20);

            // assert
            assertThat(result).hasSize(1);
        }

        @DisplayName("LIKES_DESC 정렬이면 좋아요 많은 순으로 반환된다.")
        @Test
        void returnsMostLikedFirst_whenSortIsLikesDesc() {
            // arrange
            ProductModel popular = savedProduct("에어맥스", 100);
            savedProduct("조던1", 50);
            likeFacade.addLike(1L, popular.getId());

            // act
            List<ProductInfo> result = productFacade.getProducts(SortCondition.LIKES_DESC, null, false, 0, 20);

            // assert
            assertThat(result.get(0).id()).isEqualTo(popular.getId());
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class UpdateProduct {

        @DisplayName("정상 요청이면, 이름·설명·가격이 수정된 ProductInfo를 반환한다.")
        @Test
        void returnsUpdatedProductInfo_whenRequestIsValid() {
            // arrange
            ProductModel saved = savedProduct("에어맥스", 100);

            // act
            ProductInfo result = productFacade.updateProduct(saved.getId(), "조던1", "나이키 농구화", 200000L);

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.getId()),
                () -> assertThat(result.name()).isEqualTo("조던1"),
                () -> assertThat(result.description()).isEqualTo("나이키 농구화"),
                () -> assertThat(result.price()).isEqualTo(200000L),
                () -> assertThat(result.stock()).isEqualTo(100)
            );
        }

        @DisplayName("존재하지 않는 상품 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                productFacade.updateProduct(999L, "조던1", "나이키 농구화", 200000L)
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
            ProductModel saved = savedProduct("에어맥스", 100);

            // act
            productFacade.deleteProduct(saved.getId());

            // assert
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
