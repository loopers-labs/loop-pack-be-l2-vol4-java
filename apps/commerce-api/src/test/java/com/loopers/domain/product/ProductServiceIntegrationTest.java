package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductServiceIntegrationTest {

    private static final Long BRAND_ID = 1L;
    private static final Long OTHER_BRAND_ID = 2L;
    private static final String PRODUCT_NAME = "에어맥스";
    private static final String PRODUCT_DESCRIPTION = "나이키 런닝화";
    private static final Long PRODUCT_PRICE = 150000L;

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 생성")
    @Nested
    class Create {

        @DisplayName("[ECP] 유효한 값으로 생성하면 id가 할당된 상품이 생성된다.")
        @Test
        void createsProduct_whenRequestIsValid() {
            // act
            ProductEntity result = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(BRAND_ID, result.getBrandId()),
                    () -> assertEquals(PRODUCT_NAME, result.getName()),
                    () -> assertEquals(PRODUCT_DESCRIPTION, result.getDescription()),
                    () -> assertEquals(PRODUCT_PRICE, result.getPrice())
            );
        }
    }

    @DisplayName("상품 단건 조회")
    @Nested
    class GetProduct {

        @DisplayName("[ECP] 존재하는 id로 조회하면 ProductEntity를 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            // arrange
            ProductEntity created = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);

            // act
            ProductEntity result = productService.getProduct(created.getId());

            // assert
            assertAll(
                    () -> assertEquals(created.getId(), result.getId()),
                    () -> assertEquals(PRODUCT_NAME, result.getName())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 id로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.getProduct(999L));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("상품 목록 조회")
    @Nested
    class GetAllProducts {

        @DisplayName("[ECP] brandId 없이 조회하면 전체 상품 페이지가 반환된다.")
        @Test
        void returnsAllProducts_whenNoBrandIdFilter() {
            // arrange
            productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            productService.createProduct(OTHER_BRAND_ID, "에어포스", "나이키 스니커즈", 130000L);

            // act
            Page<ProductEntity> result = productService.getAllProducts(null, PageRequest.of(0, 20));

            // assert
            assertEquals(2, result.getTotalElements());
        }

        @DisplayName("[ECP] brandId로 필터링하면 해당 브랜드의 상품만 반환된다.")
        @Test
        void returnsFilteredProducts_whenBrandIdProvided() {
            // arrange
            productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            productService.createProduct(OTHER_BRAND_ID, "에어포스", "나이키 스니커즈", 130000L);

            // act
            Page<ProductEntity> result = productService.getAllProducts(BRAND_ID, PageRequest.of(0, 20));

            // assert
            assertAll(
                    () -> assertEquals(1, result.getTotalElements()),
                    () -> assertEquals(BRAND_ID, result.getContent().get(0).getBrandId())
            );
        }

        @DisplayName("[Error Guessing] 삭제된 상품은 목록 조회에서 제외된다.")
        @Test
        void excludesDeletedProducts_fromList() {
            // arrange
            ProductEntity product = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            productService.deleteProduct(product.getId());

            // act
            Page<ProductEntity> result = productService.getAllProducts(null, PageRequest.of(0, 20));

            // assert
            assertEquals(0, result.getTotalElements());
        }
    }

    @DisplayName("브랜드별 상품 ID 목록 조회")
    @Nested
    class FindIdsByBrand {

        @DisplayName("[ECP] brandId로 조회하면 해당 브랜드의 상품 id 목록이 반환된다.")
        @Test
        void returnsProductIds_whenBrandExists() {
            // arrange
            ProductEntity product1 = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            ProductEntity product2 = productService.createProduct(BRAND_ID, "에어포스", "나이키 스니커즈", 130000L);
            productService.createProduct(OTHER_BRAND_ID, "아디다스 신발", "아디다스 런닝화", 120000L);

            // act
            List<Long> ids = productService.findIdsByBrand(BRAND_ID);

            // assert
            assertAll(
                    () -> assertEquals(2, ids.size()),
                    () -> assertTrue(ids.contains(product1.getId())),
                    () -> assertTrue(ids.contains(product2.getId()))
            );
        }

        @DisplayName("[Error Guessing] 삭제된 상품의 id는 반환되지 않는다.")
        @Test
        void excludesDeletedProductIds() {
            // arrange
            ProductEntity active = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            ProductEntity deleted = productService.createProduct(BRAND_ID, "에어포스", "나이키 스니커즈", 130000L);
            productService.deleteProduct(deleted.getId());

            // act
            List<Long> ids = productService.findIdsByBrand(BRAND_ID);

            // assert
            assertAll(
                    () -> assertEquals(1, ids.size()),
                    () -> assertTrue(ids.contains(active.getId()))
            );
        }
    }

    @DisplayName("상품 일괄 삭제")
    @Nested
    class DeleteAll {

        @DisplayName("[State Transition] 상품 id 목록으로 일괄 삭제하면 해당 상품들은 조회되지 않는다.")
        @Test
        void deletesAllProducts_thenNotFound() {
            // arrange
            ProductEntity product1 = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            ProductEntity product2 = productService.createProduct(BRAND_ID, "에어포스", "나이키 스니커즈", 130000L);

            // act
            productService.deleteAll(List.of(product1.getId(), product2.getId()));

            // assert
            assertAll(
                    () -> assertThrows(CoreException.class, () -> productService.getProduct(product1.getId())),
                    () -> assertThrows(CoreException.class, () -> productService.getProduct(product2.getId()))
            );
        }
    }

    @DisplayName("상품 수정")
    @Nested
    class UpdateProduct {

        @DisplayName("[Decision Table] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.updateProduct(999L, "변경명", "변경설명", 200000L));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Decision Table] 존재하는 상품이면 수정된다.")
        @Test
        void updatesProduct_whenProductExists() {
            // arrange
            ProductEntity product = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);

            // act
            productService.updateProduct(product.getId(), "에어포스", "나이키 스니커즈", 130000L);

            // assert
            ProductEntity updated = productService.getProduct(product.getId());
            assertAll(
                    () -> assertEquals("에어포스", updated.getName()),
                    () -> assertEquals("나이키 스니커즈", updated.getDescription()),
                    () -> assertEquals(130000L, updated.getPrice())
            );
        }
    }

    @DisplayName("상품 삭제 — State Transition: Active → Deleted")
    @Nested
    class DeleteProduct {

        @DisplayName("[State Transition] 삭제된 상품은 이후 조회 시 NOT_FOUND가 발생한다.")
        @Test
        void deletesProduct_thenNotFoundOnGet() {
            // arrange
            ProductEntity product = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);

            // act
            productService.deleteProduct(product.getId());

            // assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.getProduct(product.getId()));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[State Transition] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.deleteProduct(999L));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("좋아요 수 증가")
    @Nested
    class IncrementLikeCount {

        @DisplayName("[ECP] 존재하는 상품의 likeCount가 1 증가한다.")
        @Test
        void incrementsLikeCount_whenProductExists() {
            // arrange
            ProductEntity product = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);

            // act
            productService.incrementLikeCount(product.getId());

            // assert
            assertEquals(1L, productService.getProduct(product.getId()).getLikeCount());
        }

        @DisplayName("[ECP] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.incrementLikeCount(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("좋아요 수 감소")
    @Nested
    class DecrementLikeCount {

        @DisplayName("[ECP] 존재하는 상품의 likeCount가 1 감소한다.")
        @Test
        void decrementsLikeCount_whenProductExists() {
            // arrange
            ProductEntity product = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            productService.incrementLikeCount(product.getId());

            // act
            productService.decrementLikeCount(product.getId());

            // assert
            assertEquals(0L, productService.getProduct(product.getId()).getLikeCount());
        }

        @DisplayName("[ECP] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.decrementLikeCount(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }
}
