package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
    private EntityManagerFactory entityManagerFactory;

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

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class SearchProducts {

        @DisplayName("상품마다 브랜드를 개별 조회하지 않고, 브랜드를 한 번에 조회한다. (N+1 제거)")
        @Test
        void doesNotFetchBrandPerProduct() {
            // arrange: 브랜드 2개에 상품 10개를 분산 생성
            BrandModel nike = brandJpaRepository.save(new BrandModel("나이키", "스포츠", null));
            BrandModel adidas = brandJpaRepository.save(new BrandModel("아디다스", "스포츠", null));
            for (int i = 0; i < 10; i++) {
                BrandModel brand = (i % 2 == 0) ? nike : adidas;
                productFacade.createProduct(brand.getId(), "상품" + i, "설명" + i, 1000L + i, 10);
            }

            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();

            // act
            ProductPageInfo result = productFacade.searchProducts(null, "latest", "desc", 0, 20);

            // assert
            assertAll(
                () -> assertThat(result.content()).hasSize(10),
                // 상품마다 브랜드를 조회했다면 10건 이상의 쿼리가 나간다.
                // 배치 조회면 (상품 검색 + count + 브랜드 1회) 수준으로 일정하게 유지된다.
                () -> assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4),
                () -> assertThat(result.content())
                    .extracting(ProductDisplayInfo::brandName)
                    .containsOnly("나이키", "아디다스")
            );
        }
    }
}
