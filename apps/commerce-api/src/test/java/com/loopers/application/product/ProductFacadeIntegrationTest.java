package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.product.SortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductFacadeIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private BrandModel saveBrand(String name) {
        return brandRepository.save(new BrandModel(name));
    }

    private ProductModel saveProduct(Long brandId, String name, BigDecimal price) {
        ProductModel product = productRepository.save(new ProductModel(brandId, name, price));
        productStatsRepository.save(new ProductStatsModel(product));
        return product;
    }

    private void saveStock(Long productId, Long quantity) {
        stockRepository.save(new StockModel(productId, quantity));
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 브랜드 ID이면 재고를 포함한 ProductAdminInfo가 반환된다.")
        @Test
        void returnsProductAdminInfo_whenBrandExists() {
            // given
            BrandModel brand = saveBrand("Nike");

            // when
            ProductAdminInfo result = productFacade.createProductForAdmin(brand.getId(), "에어맥스", BigDecimal.valueOf(150000), 10L);

            // then
            assertAll(
                    () -> assertThat(result.brandName()).isEqualTo("Nike"),
                    () -> assertThat(result.name()).isEqualTo("에어맥스"),
                    () -> assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(150000)),
                    () -> assertThat(result.likeCount()).isEqualTo(0L),
                    () -> assertThat(result.stock()).isEqualTo(10L)
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productFacade.createProductForAdmin(999L, "에어맥스", BigDecimal.valueOf(150000), 10L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID이면 브랜드명이 포함된 ProductInfo가 반환된다.")
        @Test
        void returnsProductInfo_whenProductExists() {
            // given
            BrandModel brand = saveBrand("Adidas");
            ProductModel product = saveProduct(brand.getId(), "울트라부스트", BigDecimal.valueOf(200000));
            saveStock(product.getId(), 5L);

            // when
            ProductInfo result = productFacade.getProduct(product.getId());

            // then
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(product.getId()),
                    () -> assertThat(result.brandName()).isEqualTo("Adidas"),
                    () -> assertThat(result.name()).isEqualTo("울트라부스트"),
                    () -> assertThat(result.inStock()).isTrue()
            );
        }

        @DisplayName("존재하지 않는 상품 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productFacade.getProduct(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 상세를 캐시로 조회할 때,")
    @Nested
    class ProductCache {

        @DisplayName("캐시에 적재된 이후에는 DB 값이 바뀌어도 캐시된 값이 그대로 반환된다.")
        @Test
        void returnsCachedValue_evenAfterDatabaseIsChanged() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "에어맥스", BigDecimal.valueOf(150000));
            saveStock(product.getId(), 10L);
            ProductInfo cached = productFacade.getProduct(product.getId());

            product.update("변경된 이름", BigDecimal.valueOf(999999));
            productRepository.save(product);

            // when
            ProductInfo result = productFacade.getProduct(product.getId());

            // then
            assertAll(
                    () -> assertThat(result.name()).isEqualTo(cached.name()),
                    () -> assertThat(result.price()).isEqualByComparingTo(cached.price())
            );
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProducts {

        @DisplayName("정렬 기준별로 브랜드 정보가 포함된 상품 목록이 반환된다.")
        @ParameterizedTest
        @EnumSource(SortType.class)
        void returnsProductInfoPage_withBrandInfo(SortType sortType) {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel productA = saveProduct(brand.getId(), "상품A", BigDecimal.valueOf(10000));
            ProductModel productB = saveProduct(brand.getId(), "상품B", BigDecimal.valueOf(20000));
            saveStock(productA.getId(), 5L);
            saveStock(productB.getId(), 3L);

            // when
            Page<ProductInfo> result = productFacade.getProducts(null, PageRequest.of(0, 20, sortType.toSort()));

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).allMatch(info -> info.brandName().equals("Nike"));
        }

        @DisplayName("상품이 없으면 빈 페이지가 반환된다.")
        @Test
        void returnsEmptyPage_whenNoProductsExist() {
            // when
            Page<ProductInfo> result = productFacade.getProducts(null, PageRequest.of(0, 20, SortType.LATEST.toSort()));

            // then
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }

        @DisplayName("brandId로 필터링하면 해당 브랜드의 상품만 반환된다.")
        @Test
        void returnsFilteredProducts_whenBrandIdIsProvided() {
            // given
            BrandModel brandA = saveBrand("Nike");
            BrandModel brandB = saveBrand("Adidas");
            ProductModel productA = saveProduct(brandA.getId(), "나이키 상품", BigDecimal.valueOf(10000));
            ProductModel productB = saveProduct(brandB.getId(), "아디다스 상품", BigDecimal.valueOf(20000));
            saveStock(productA.getId(), 5L);
            saveStock(productB.getId(), 3L);

            // when
            Page<ProductInfo> result = productFacade.getProducts(brandA.getId(), PageRequest.of(0, 20, SortType.LATEST.toSort()));

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).brandName()).isEqualTo("Nike");
        }
    }

    @DisplayName("상품 목록을 캐시로 조회할 때,")
    @Nested
    class ProductListCache {

        @DisplayName("캐시에 적재된 이후 새 상품이 추가돼도 totalElements를 포함해 캐시된 결과가 그대로 반환된다.")
        @Test
        void returnsCachedPage_evenAfterNewProductIsAdded() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel productA = saveProduct(brand.getId(), "상품A", BigDecimal.valueOf(10000));
            saveStock(productA.getId(), 5L);
            Page<ProductInfo> cached = productFacade.getProducts(brand.getId(), PageRequest.of(0, 20, SortType.LATEST.toSort()));

            ProductModel productB = saveProduct(brand.getId(), "상품B", BigDecimal.valueOf(20000));
            saveStock(productB.getId(), 3L);

            // when
            Page<ProductInfo> result = productFacade.getProducts(brand.getId(), PageRequest.of(0, 20, SortType.LATEST.toSort()));

            // then
            assertAll(
                    () -> assertThat(result.getTotalElements()).isEqualTo(cached.getTotalElements()),
                    () -> assertThat(result.getContent()).hasSize(cached.getContent().size())
            );
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class UpdateProduct {

        @DisplayName("존재하는 상품이면 수정된 정보가 포함된 ProductAdminInfo가 반환된다.")
        @Test
        void returnsUpdatedProductAdminInfo_whenProductExists() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "기존 상품", BigDecimal.valueOf(100000));
            saveStock(product.getId(), 10L);

            // when
            ProductAdminInfo result = productFacade.updateProductForAdmin(product.getId(), "수정 상품", BigDecimal.valueOf(200000), 20L);

            // then
            assertAll(
                    () -> assertThat(result.name()).isEqualTo("수정 상품"),
                    () -> assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(200000)),
                    () -> assertThat(result.stock()).isEqualTo(20L)
            );
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productFacade.updateProductForAdmin(999L, "상품", BigDecimal.valueOf(100000), 5L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 상품이면 예외 없이 완료된다.")
        @Test
        void deletesProduct_whenProductExists() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "상품", BigDecimal.valueOf(100000));
            saveStock(product.getId(), 10L);

            // when & then
            assertDoesNotThrow(() -> productFacade.deleteProduct(product.getId()));
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productFacade.deleteProduct(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("상품이 삭제되면 product_stats도 함께 soft delete된다.")
        @Test
        void softDeletesProductStats_whenProductIsDeleted() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "상품", BigDecimal.valueOf(100000));
            saveStock(product.getId(), 10L);

            // when
            productFacade.deleteProduct(product.getId());

            // then - @SQLRestriction으로 인해 soft delete된 stats는 조회되지 않음
            assertThat(productStatsRepository.findByProduct(product)).isEmpty();
        }
    }

    @DisplayName("브랜드별 상품 목록을 조회할 때,")
    @Nested
    class GetProductsByBrandId {

        @DisplayName("존재하는 브랜드 ID이면 해당 브랜드의 상품 목록이 반환된다.")
        @Test
        void returnsProductInfoPage_whenBrandExists() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel productA = saveProduct(brand.getId(), "상품A", BigDecimal.valueOf(10000));
            ProductModel productB = saveProduct(brand.getId(), "상품B", BigDecimal.valueOf(20000));
            saveStock(productA.getId(), 5L);
            saveStock(productB.getId(), 3L);

            // when
            Page<ProductInfo> result = productFacade.getProductsByBrandId(brand.getId(), PageRequest.of(0, 20));

            // then
            assertAll(
                    () -> assertThat(result.getTotalElements()).isEqualTo(2),
                    () -> assertThat(result.getContent()).allMatch(info -> info.brandName().equals("Nike"))
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productFacade.getProductsByBrandId(999L, PageRequest.of(0, 20)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("관리자용 상품을 단건 조회할 때,")
    @Nested
    class GetProductForAdmin {

        @DisplayName("존재하는 상품 ID이면 재고를 포함한 ProductAdminInfo가 반환된다.")
        @Test
        void returnsProductAdminInfo_whenProductExists() {
            // given
            BrandModel brand = saveBrand("Adidas");
            ProductModel product = saveProduct(brand.getId(), "울트라부스트", BigDecimal.valueOf(200000));
            saveStock(product.getId(), 7L);

            // when
            ProductAdminInfo result = productFacade.getProductForAdmin(product.getId());

            // then
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(product.getId()),
                    () -> assertThat(result.brandName()).isEqualTo("Adidas"),
                    () -> assertThat(result.name()).isEqualTo("울트라부스트"),
                    () -> assertThat(result.stock()).isEqualTo(7L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productFacade.getProductForAdmin(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
