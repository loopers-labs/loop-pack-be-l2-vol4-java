package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;  // 테스트 데이터 준비용

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 입력으로 생성 시, 저장된 상품을 반환한다.")
        @Test
        void returnsSavedProduct_whenValidInput() {
            // act
            ProductModel saved = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getName()).isEqualTo(ProductFixture.NAME),
                () -> assertThat(saved.getPrice()).isEqualTo(ProductFixture.PRICE),
                () -> assertThat(saved.getBrand().getId()).isEqualTo(brand.getId()),
                () -> assertThat(saved.getDeletedAt()).isNull()
            );
        }
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class Get {

        @DisplayName("존재하지 않는 ID 조회 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdNotExists() {
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.get(UUID.randomUUID())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 상품을 어드민용 get으로 조회 시, 반환된다.")
        @Test
        void returnProduct_whenDeletedAndAdmin() {
            // arrange
            ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);
            productService.delete(product.getId());

            // act
            ProductModel found = productService.get(product.getId());

            assertThat(found.getId()).isEqualTo(product.getId());
        }

        @DisplayName("삭제된 상품을 고객용 getActive로 조회 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenDeletedAndCustomer() {
            // arrange
            ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);
            productService.delete(product.getId());

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.getActive(product.getId())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("삭제 후 deletedAt이 기록된다.")
        @Test
        void softDeletes_whenDeleteCalled() {
            // arrange
            ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);

            // act
            productService.delete(product.getId());

            // assert
            ProductModel deleted = productService.get(product.getId());
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("deleteByBrand 호출 시, 브랜드 산하 상품이 모두 소프트딜리트된다.")
        @Test
        void softDeletesAllProducts_whenDeleteByBrand() {
            // arrange — 동일 브랜드에 상품 2개
            productService.create(brand, "상품A", "설명A", 10_000L);
            productService.create(brand, "상품B", "설명B", 20_000L);

            // act
            productService.deleteByBrand(brand.getId());

            // assert — 고객 목록 조회 시 빈 결과
            Page<ProductModel> activeList = productService.getActiveList(null, PageRequest.of(0, 10));
            assertThat(activeList.getTotalElements()).isZero();
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetList {

        @DisplayName("고객 목록 조회 시 삭제된 상품은 제외된다.")
        @Test
        void excludesDeletedProducts_whenGetActiveList() {
            // arrange
            ProductModel active = productService.create(brand, "활성상품", "설명", 10_000L);
            ProductModel deleted = productService.create(brand, "삭제상품", "설명", 20_000L);
            productService.delete(deleted.getId());

            // act
            Page<ProductModel> page = productService.getActiveList(null, PageRequest.of(0, 10));

            // assert
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(1),
                () -> assertThat(page.getContent().get(0).getId()).isEqualTo(active.getId())
            );
        }
    }
}
