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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductServiceIntegrationTest {

    private static final Long BRAND_ID = 1L;
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

        @DisplayName("[ECP] 생성된 상품 수만큼 목록이 반환된다.")
        @Test
        void returnsAllProducts() {
            // arrange
            productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            productService.createProduct(BRAND_ID, "에어포스", "나이키 스니커즈", 130000L);

            // act
            List<ProductEntity> result = productService.getAllProducts();

            // assert
            assertEquals(2, result.size());
        }

        @DisplayName("[Error Guessing] 삭제된 상품은 목록 조회에서 제외된다.")
        @Test
        void excludesDeletedProducts_fromList() {
            // arrange
            ProductEntity product = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
            productService.deleteProduct(product.getId());

            // act
            List<ProductEntity> result = productService.getAllProducts();

            // assert
            assertEquals(0, result.size());
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
}
