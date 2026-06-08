package com.loopers.domain.product;

import com.loopers.domain.product.enums.ProductStatus;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductServiceUnitTest {

    private InMemoryProductRepository productRepository;
    private ProductService sut;

    private static final Long BRAND_ID = 1L;
    private static final Long NON_EXISTENT_ID = 999L;
    private static final Long PRODUCT_ID = 0L;
    private static final String DEFAULT_NAME = "테스트상품";
    private static final String OTHER_NAME = "수정상품";

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        sut = new ProductService(productRepository);
    }

    private ProductModel saveDefaultProduct() {
        return productRepository.save(new ProductModel(BRAND_ID, new ProductName(DEFAULT_NAME)));
    }

    @DisplayName("상품 단건 조회 시,")
    @Nested
    class Get {

        @DisplayName("상품이 존재하면, 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            saveDefaultProduct();

            ProductModel result = sut.get(PRODUCT_ID);

            assertThat(result.getName()).isEqualTo(DEFAULT_NAME);
        }

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.get(NON_EXISTENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 입력이면, 상품이 저장되어 반환된다.")
        @Test
        void createsProduct_whenInputsAreValid() {
            ProductModel result = sut.create(BRAND_ID, new ProductName(DEFAULT_NAME));

            assertThat(result.getName()).isEqualTo(DEFAULT_NAME);
        }

        @DisplayName("같은 브랜드 내 동일한 이름의 상품이 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenProductNameAlreadyExistsInBrand() {
            saveDefaultProduct();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.create(BRAND_ID, new ProductName(DEFAULT_NAME)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("상품 수정 시,")
    @Nested
    class Update {

        @DisplayName("유효한 이름이면, 상품명이 변경된다.")
        @Test
        void updatesProductName_whenNameIsValid() {
            saveDefaultProduct();

            ProductModel result = sut.update(PRODUCT_ID, new ProductName(OTHER_NAME));

            assertThat(result.getName()).isEqualTo(OTHER_NAME);
        }

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.update(NON_EXISTENT_ID, new ProductName(OTHER_NAME)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 삭제 시,")
    @Nested
    class Delete {

        @DisplayName("상품이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.delete(NON_EXISTENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드별 상품 판매 중단 시,")
    @Nested
    class SuspendAllByBrandId {

        @DisplayName("해당 브랜드의 모든 상품이 INACTIVE 로 변경된다.")
        @Test
        void suspendsAllProducts_whenBrandIdMatches() {
            productRepository.save(new ProductModel(BRAND_ID, new ProductName("상품A")));
            productRepository.save(new ProductModel(BRAND_ID, new ProductName("상품B")));

            sut.suspendAllByBrandId(BRAND_ID);

            productRepository.findAllByBrandId(BRAND_ID)
                    .forEach(p -> assertThat(p.getStatus()).isEqualTo(ProductStatus.INACTIVE));
        }
    }
}
