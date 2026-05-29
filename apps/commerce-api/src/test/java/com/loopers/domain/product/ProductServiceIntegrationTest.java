package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class ProductServiceIntegrationTest {

    private final BrandService brandService;
    private final ProductService productService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductServiceIntegrationTest(
        BrandService brandService,
        ProductService productService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.brandService = brandService;
        this.productService = productService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("고객 노출 상품 상세를 조회할 때 ")
    @Nested
    class GetVisibleProduct {

        @DisplayName("미삭제 상품과 미삭제 브랜드가 주어지면, 상품을 반환한다.")
        @Test
        void returnsProduct_whenProductAndBrandAreActive() {
            // arrange
            Brand brand = createBrand();
            Product product = createProduct(brand, "아이폰 16 Pro", 1_550_000L);

            // act
            Product found = productService.getVisibleProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(found.getId()).isEqualTo(product.getId()),
                () -> assertThat(found.getBrandId()).isEqualTo(brand.getId()),
                () -> assertThat(found.getName()).isEqualTo("아이폰 16 Pro")
            );
        }

        @DisplayName("삭제된 브랜드의 상품 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIsDeleted() {
            // arrange
            Brand brand = createBrand();
            Product product = createProduct(brand, "아이폰 16 Pro", 1_550_000L);
            brandService.deleteBrand(brand.getId());

            // act & assert
            assertThatThrownBy(() -> productService.getVisibleProduct(product.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("고객 노출 상품 목록을 조회할 때 ")
    @Nested
    class GetVisibleProducts {

        @DisplayName("삭제된 상품과 삭제된 브랜드의 상품은 목록에서 제외한다.")
        @Test
        void excludesDeletedProductAndDeletedBrand_whenProductsExist() {
            // arrange
            Brand activeBrand = createBrand();
            Product activeProduct = createProduct(activeBrand, "아이폰 16", 1_250_000L);
            Product deletedProduct = createProduct(activeBrand, "아이폰 16 Pro", 1_550_000L);
            Brand deletedBrand = createBrand("애플 스토어");
            createProduct(deletedBrand, "아이폰 16 Pro Max", 1_900_000L);
            productService.deleteProduct(deletedProduct.getId());
            brandService.deleteBrand(deletedBrand.getId());

            // act
            PageResult<Product> products = productService.getVisibleProducts(
                new PageQuery(0, 20),
                null,
                ProductSort.LATEST
            );

            // assert
            assertAll(
                () -> assertThat(products.content()).extracting(Product::getId).containsExactly(activeProduct.getId()),
                () -> assertThat(products.totalElements()).isEqualTo(1)
            );
        }

        @DisplayName("삭제된 브랜드 ID로 필터링하면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenBrandFilterIsDeleted() {
            // arrange
            Brand brand = createBrand();
            createProduct(brand, "아이폰 16 Pro", 1_550_000L);
            brandService.deleteBrand(brand.getId());

            // act
            PageResult<Product> products = productService.getVisibleProducts(
                new PageQuery(0, 20),
                brand.getId(),
                ProductSort.LATEST
            );

            // assert
            assertAll(
                () -> assertThat(products.content()).isEmpty(),
                () -> assertThat(products.totalElements()).isZero()
            );
        }
    }

    private Brand createBrand() {
        return createBrand("애플");
    }

    private Brand createBrand(String name) {
        return brandService.createBrand(name, "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
    }

    private Product createProduct(Brand brand, String name, long price) {
        return productService.createProduct(
            brand.getId(),
            name,
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            price
        );
    }
}
