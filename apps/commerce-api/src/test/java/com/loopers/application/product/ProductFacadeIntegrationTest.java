package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductFacadeIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    class GetProductDetail {

        @DisplayName("상품과 브랜드 정보를 조합해서 반환한다.")
        @Test
        void returnsProductWithBrand_whenProductExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(
                new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png")
            );
            ProductInfo created = productFacade.createProduct(
                brand.getId(), "신발", "편한 신발", 10000L, 5
            );

            // act
            ProductDetailInfo detail = productFacade.getProductDetail(created.id());

            // assert
            assertAll(
                () -> assertThat(detail.id()).isEqualTo(created.id()),
                () -> assertThat(detail.name()).isEqualTo("신발"),
                () -> assertThat(detail.price()).isEqualTo(10000L),
                () -> assertThat(detail.brand().id()).isEqualTo(brand.getId()),
                () -> assertThat(detail.brand().name()).isEqualTo("나이키"),
                () -> assertThat(detail.brand().logoUrl()).isEqualTo("https://example.com/nike.png")
            );
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productFacade.getProductDetail(999L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("상품의 브랜드가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            BrandModel brand = brandJpaRepository.save(
                new BrandModel("사라질 브랜드", null, null)
            );
            ProductInfo created = productFacade.createProduct(
                brand.getId(), "신발", "편한 신발", 10000L, 5
            );
            brandJpaRepository.deleteById(brand.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productFacade.getProductDetail(created.id());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
