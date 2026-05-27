package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductDomainServiceTest {

    private final ProductDomainService productDomainService = new ProductDomainService();

    private BrandModel activeBrand;
    private BrandModel deletedBrand;
    private ProductModel activeProduct;
    private ProductModel deletedProduct;

    @BeforeEach
    void setUp() {
        activeBrand = new BrandModel("Nike", "스포츠 브랜드");
        deletedBrand = new BrandModel("구브랜드", "설명");
        deletedBrand.delete();

        activeProduct = new ProductModel(activeBrand, "나이키 에어맥스", 150_000);
        deletedProduct = new ProductModel(activeBrand, "단종 상품", 100_000);
        deletedProduct.delete();
    }

    @DisplayName("validateBrand()를 호출할 때,")
    @Nested
    class ValidateBrand {

        @DisplayName("활성 브랜드는 검증을 통과한다.")
        @Test
        void doesNotThrow_whenBrandIsActive() {
            assertDoesNotThrow(() -> productDomainService.validateBrand(activeBrand));
        }

        @DisplayName("삭제된 브랜드는 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIsDeleted() {
            CoreException result = assertThrows(CoreException.class,
                () -> productDomainService.validateBrand(deletedBrand)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("validateProductActive()를 호출할 때,")
    @Nested
    class ValidateProductActive {

        @DisplayName("활성 상품은 검증을 통과한다.")
        @Test
        void doesNotThrow_whenProductIsActive() {
            assertDoesNotThrow(() -> productDomainService.validateProductActive(activeProduct));
        }

        @DisplayName("삭제된 상품은 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIsDeleted() {
            CoreException result = assertThrows(CoreException.class,
                () -> productDomainService.validateProductActive(deletedProduct)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
