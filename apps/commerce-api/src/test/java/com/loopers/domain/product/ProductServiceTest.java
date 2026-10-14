package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductServiceTest {

    private ProductService productService;
    private FakeProductRepository fakeRepository;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeProductRepository();
        productService = new ProductService(fakeRepository);
    }

    private ProductModel save(Long brandId, String name, long price, int stock) {
        return productService.createProduct(brandId, name, "설명", price, stock, null);
    }

    @DisplayName("상품을 조회할 때, ")
    @Nested
    class GetProduct {
        @DisplayName("존재하지 않는 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productService.getProduct(999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class ListProducts {
        @DisplayName("brandId 가 null 이면 전체 브랜드 상품을 반환한다.")
        @Test
        void returnsAll_whenBrandIdIsNull() {
            // arrange
            save(1L, "A", 1000L, 5);
            save(2L, "B", 2000L, 5);

            // act
            List<ProductModel> result = productService.listProducts(ProductSortType.LATEST, null);

            // assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("brandId 가 주어지면 해당 브랜드의 상품만 반환한다.")
        @Test
        void filtersByBrand() {
            // arrange
            save(1L, "A", 1000L, 5);
            save(1L, "B", 2000L, 5);
            save(2L, "C", 3000L, 5);

            // act
            List<ProductModel> result = productService.listProducts(ProductSortType.LATEST, 1L);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(p -> p.getBrandId() == 1L);
        }

        @DisplayName("PRICE_ASC 정렬이면, 가격 오름차순으로 반환한다.")
        @Test
        void sortsByPriceAsc() {
            // arrange
            save(1L, "C", 3000L, 5);
            save(1L, "A", 1000L, 5);
            save(1L, "B", 2000L, 5);

            // act
            List<ProductModel> result = productService.listProducts(ProductSortType.PRICE_ASC, null);

            // assert
            assertThat(result).extracting(ProductModel::getPrice)
                .containsExactly(1000L, 2000L, 3000L);
        }

        @DisplayName("sortType 이 null 이면 기본값 LATEST 로 처리한다.")
        @Test
        void defaultsToLatest_whenSortIsNull() {
            // arrange
            save(1L, "A", 1000L, 5);

            // act
            List<ProductModel> result = productService.listProducts(null, null);

            // assert
            assertThat(result).hasSize(1);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DecreaseStock {
        @DisplayName("정상이면 도메인 메서드를 통해 차감되고 저장된다.")
        @Test
        void delegatesToDomain() {
            // arrange
            ProductModel saved = save(1L, "A", 1000L, 10);

            // act
            ProductModel result = productService.decreaseStock(saved.getId(), 3);

            // assert
            assertThat(result.getStock()).isEqualTo(7);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productService.decreaseStock(999L, 1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
