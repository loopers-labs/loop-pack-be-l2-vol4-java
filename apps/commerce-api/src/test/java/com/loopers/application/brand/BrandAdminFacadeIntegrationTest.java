package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
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

/**
 * BrandAdminFacade 통합 — BrandService + ProductService.count 합성을 검증한다.
 * 단건/목록 모두 *상품 없는 브랜드*가 productCount=0으로 채워지는지 확인.
 */
@SpringBootTest
class BrandAdminFacadeIntegrationTest {

    @Autowired
    private BrandAdminFacade brandAdminFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandWith3ProductsId;
    private Long brandWithoutProductId;

    @BeforeEach
    void setUp() {
        BrandModel a = brandRepository.save(new BrandModel("LoopersA", "감성"));
        BrandModel b = brandRepository.save(new BrandModel("LoopersB", "심플"));
        brandWith3ProductsId = a.getId();
        brandWithoutProductId = b.getId();

        productRepository.save(new ProductModel(a.getId(), "p1", "desc", 1_000L));
        productRepository.save(new ProductModel(a.getId(), "p2", "desc", 2_000L));
        productRepository.save(new ProductModel(a.getId(), "p3", "desc", 3_000L));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("어드민 브랜드 단건 조회 시")
    @Nested
    class GetBrand {

        @DisplayName("브랜드가 존재하면 productCount가 합성되어 반환된다")
        @Test
        void returnsBrandWithProductCount() {
            // when
            BrandAdminInfo info = brandAdminFacade.getBrand(brandWith3ProductsId);

            // then
            assertAll(
                () -> assertThat(info.id()).isEqualTo(brandWith3ProductsId),
                () -> assertThat(info.name()).isEqualTo("LoopersA"),
                () -> assertThat(info.productCount()).isEqualTo(3L)
            );
        }

        @DisplayName("상품이 한 개도 없는 브랜드는 productCount=0으로 반환된다")
        @Test
        void returnsZeroCount_whenNoProducts() {
            // when
            BrandAdminInfo info = brandAdminFacade.getBrand(brandWithoutProductId);

            // then
            assertThat(info.productCount()).isZero();
        }

        @DisplayName("브랜드가 존재하지 않으면 NOT_FOUND가 발생한다")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> brandAdminFacade.getBrand(99_999L));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("어드민 브랜드 목록 조회 시")
    @Nested
    class Search {

        @DisplayName("페이지의 각 브랜드에 productCount가 매핑되며 상품 없는 브랜드는 0으로 채워진다")
        @Test
        void mapsProductCount_andDefaultsToZero() {
            // when
            Page<BrandAdminInfo> page = brandAdminFacade.search(PageRequest.of(0, 20));

            // then
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(2),
                () -> assertThat(page.getContent())
                    .filteredOn(info -> info.id().equals(brandWith3ProductsId))
                    .singleElement().extracting(BrandAdminInfo::productCount).isEqualTo(3L),
                () -> assertThat(page.getContent())
                    .filteredOn(info -> info.id().equals(brandWithoutProductId))
                    .singleElement().extracting(BrandAdminInfo::productCount).isEqualTo(0L)
            );
        }
    }
}
