package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.SortType;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private BrandModel savedBrand;

    @BeforeEach
    void setUp() {
        savedBrand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductInfo createProduct(String name, int price, int initialStock) {
        return productService.create(new ProductCreateCommand(savedBrand.getId(), name, price, initialStock));
    }

    @DisplayName("create()를 호출할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 커맨드로 생성 시 DB에 Product와 Stock이 저장되고 ProductInfo가 반환된다.")
        @Test
        void savesProductAndStock_whenValidCommandProvided() {
            // act
            ProductInfo result = createProduct("나이키 에어맥스", 150_000, 100);

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(result.price()).isEqualTo(150_000),
                () -> assertThat(result.brandName()).isEqualTo("Nike"),
                () -> assertThat(result.stockQuantity()).isEqualTo(100),
                () -> assertThat(productJpaRepository.findById(result.id())).isPresent(),
                () -> assertThat(stockJpaRepository.findByProduct_Id(result.id())).isPresent()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandDoesNotExist() {
            CoreException result = assertThrows(CoreException.class, () ->
                productService.create(new ProductCreateCommand(999L, "상품명", 10_000, 10))
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("getById()를 호출할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 활성 상품 조회 시 ProductInfo가 반환된다.")
        @Test
        void returnsProductInfo_whenProductExists() {
            // arrange
            ProductInfo created = createProduct("나이키 에어맥스", 150_000, 50);

            // act
            ProductInfo result = productService.getById(created.id());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.name()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(result.stockQuantity()).isEqualTo(50)
            );
        }

        @DisplayName("소프트딜리트된 상품 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductIsSoftDeleted() {
            // arrange
            ProductInfo created = createProduct("나이키 에어맥스", 150_000, 10);
            productService.delete(created.id());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productService.getById(created.id())
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getAll()를 호출할 때,")
    @Nested
    class GetAll {

        @DisplayName("활성 상품만 페이지에 포함된다.")
        @Test
        void returnsOnlyActiveProducts() {
            // arrange
            createProduct("나이키 에어맥스", 150_000, 10);
            ProductInfo deleted = createProduct("나이키 조던", 200_000, 5);
            productService.delete(deleted.id());

            // act
            Page<ProductInfo> result = productService.getAll(
                PageRequest.of(0, 20), ProductSearchCondition.of(null, SortType.LATEST)
            );

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("나이키 에어맥스");
        }

        @DisplayName("brandId 필터 적용 시 해당 브랜드의 상품만 반환된다.")
        @Test
        void returnsFilteredProducts_whenBrandIdProvided() {
            // arrange
            BrandModel anotherBrand = brandJpaRepository.save(new BrandModel("Adidas", "독일 스포츠"));
            createProduct("나이키 에어맥스", 150_000, 10);
            productService.create(new ProductCreateCommand(anotherBrand.getId(), "아디다스 삼바", 120_000, 20));

            // act
            Page<ProductInfo> result = productService.getAll(
                PageRequest.of(0, 20), ProductSearchCondition.of(savedBrand.getId(), SortType.LATEST)
            );

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).brandName()).isEqualTo("Nike");
        }
    }

    @DisplayName("update()를 호출할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정 시 변경 내용이 DB에 반영된다.")
        @Test
        void updatesPersisted_whenValidCommandProvided() {
            // arrange
            ProductInfo created = createProduct("나이키 에어맥스", 150_000, 10);

            // act
            ProductInfo result = productService.update(created.id(), new ProductUpdateCommand("뉴발란스 990", 200_000));

            // assert
            ProductModel updated = productJpaRepository.findById(created.id()).orElseThrow();
            assertAll(
                () -> assertThat(result.name()).isEqualTo("뉴발란스 990"),
                () -> assertThat(updated.getName()).isEqualTo("뉴발란스 990"),
                () -> assertThat(updated.getPrice()).isEqualTo(200_000),
                () -> assertThat(updated.getBrand().getId()).isEqualTo(savedBrand.getId()) // 브랜드 불변
            );
        }
    }

    @DisplayName("delete()를 호출할 때,")
    @Nested
    class Delete {

        @DisplayName("상품 삭제 시 DB에 소프트딜리트되어 deleted_at이 설정된다.")
        @Test
        void softDeletesProductInDb_whenCalled() {
            // arrange
            ProductInfo created = createProduct("나이키 에어맥스", 150_000, 10);

            // act
            productService.delete(created.id());

            // assert
            ProductModel found = productJpaRepository.findById(created.id()).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
        }
    }

}
