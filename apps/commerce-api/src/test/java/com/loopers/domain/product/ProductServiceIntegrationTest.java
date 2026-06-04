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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class ProductServiceIntegrationTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("상품을 생성할 때")
    class CreateProduct {

        @DisplayName("유효한 정보로 생성하면, 좋아요 0·활성 상태로 저장된다.")
        @Test
        void given_validInput_when_createProduct_then_savedWithZeroLikes() {
            ProductModel result = productService.createProduct(1L, "에어맥스", "러닝화", "http://img/a.png", 139000L);

            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getBrandId()).isEqualTo(1L),
                    () -> assertThat(result.getLikesCount()).isEqualTo(0L),
                    () -> assertThat(result.isActive()).isTrue()
            );
        }
    }

    @Nested
    @DisplayName("활성 상품을 조회할 때")
    class GetActiveProduct {

        @DisplayName("존재하는 활성 상품을 조회하면, 상품을 반환한다.")
        @Test
        void given_activeProduct_when_getActiveProduct_then_returns() {
            ProductModel saved = productService.createProduct(1L, "에어맥스", "러닝화", null, 139000L);

            ProductModel result = productService.getActiveProduct(saved.getId());

            assertThat(result.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("존재하지 않는 id로 조회하면, NotFound 예외가 발생한다.")
        @Test
        void given_nonExistingId_when_getActiveProduct_then_throwsNotFound() {
            Throwable thrown = catchThrowable(() -> productService.getActiveProduct(9999L));

            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된(비활성) 상품을 조회하면, NotFound 예외가 발생한다.")
        @Test
        void given_deletedProduct_when_getActiveProduct_then_throwsNotFound() {
            ProductModel saved = productService.createProduct(1L, "에어맥스", "러닝화", null, 139000L);
            saved.delete();
            productRepository.save(saved);

            Throwable thrown = catchThrowable(() -> productService.getActiveProduct(saved.getId()));

            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
