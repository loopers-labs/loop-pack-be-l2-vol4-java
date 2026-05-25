package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
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
public class ProductFacadeIntegrationTest {

    @Autowired ProductFacade productFacade;
    @Autowired BrandService brandService;
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

        @DisplayName("활성 브랜드의 상품을 생성하면, 저장된다.")
        @Test
        void given_activeBrand_when_createProduct_then_saved() {
            BrandModel brand = brandService.register("나이키", "스포츠");

            ProductInfo result = productFacade.createProduct(brand.getId(), "에어맥스", "러닝화", null, 139000L, 10);

            assertAll(
                    () -> assertThat(result.id()).isNotNull(),
                    () -> assertThat(result.brandId()).isEqualTo(brand.getId())
            );
        }

        @DisplayName("존재하지 않는 브랜드로 상품을 생성하면, NotFound 예외가 발생한다.")
        @Test
        void given_nonExistingBrand_when_createProduct_then_throwsNotFound() {
            Throwable thrown = catchThrowable(() ->
                    productFacade.createProduct(9999L, "에어맥스", "러닝화", null, 139000L, 10));

            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("상품 상세를 조회할 때")
    class GetProductDetail {

        @DisplayName("활성 상품을 조회하면, Product와 Brand가 조합된 상세를 반환한다.")
        @Test
        void given_activeProductAndBrand_when_getProductDetail_then_returnsCombined() {
            BrandModel brand = brandService.register("나이키", "스포츠");
            ProductInfo product = productFacade.createProduct(brand.getId(), "에어맥스", "러닝화", "http://img/a.png", 139000L, 10);

            ProductDetailInfo result = productFacade.getProductDetail(product.id());

            assertAll(
                    () -> assertThat(result.id()).isEqualTo(product.id()),
                    () -> assertThat(result.name()).isEqualTo("에어맥스"),
                    () -> assertThat(result.brandId()).isEqualTo(brand.getId()),
                    () -> assertThat(result.brandName()).isEqualTo("나이키"),
                    () -> assertThat(result.likesCount()).isEqualTo(0L)
            );
        }

        @DisplayName("삭제된(비활성) 상품을 조회하면, NotFound 예외가 발생한다.")
        @Test
        void given_deletedProduct_when_getProductDetail_then_throwsNotFound() {
            BrandModel brand = brandService.register("나이키", "스포츠");
            ProductInfo product = productFacade.createProduct(brand.getId(), "에어맥스", "러닝화", null, 139000L, 10);
            ProductModel saved = productService.getProduct(product.id());
            saved.delete();
            productRepository.save(saved);

            Throwable thrown = catchThrowable(() -> productFacade.getProductDetail(product.id()));

            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
