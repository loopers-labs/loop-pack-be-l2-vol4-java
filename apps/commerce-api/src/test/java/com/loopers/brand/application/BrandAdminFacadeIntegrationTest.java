package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class BrandAdminFacadeIntegrationTest {

    private final BrandAdminFacade brandAdminFacade;
    private final BrandService brandService;
    private final ProductService productService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    BrandAdminFacadeIntegrationTest(
        BrandAdminFacade brandAdminFacade,
        BrandService brandService,
        ProductService productService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.brandAdminFacade = brandAdminFacade;
        this.brandService = brandService;
        this.productService = productService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 삭제할 때 ")
    @Nested
    class DeleteBrand {

        @DisplayName("소속 상품이 있으면, 브랜드와 소속 상품을 함께 삭제 상태로 변경한다.")
        @Test
        void deletesBrandAndProducts_whenBrandHasProducts() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product product = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );

            // act
            brandAdminFacade.deleteBrand(brand.getId());

            // assert
            assertAll(
                () -> assertThatThrownBy(() -> brandService.getBrand(brand.getId()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThatThrownBy(() -> productService.getProduct(product.getId()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND)
            );
        }
    }
}
