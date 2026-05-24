package com.loopers.application.product;

import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductFacadeIntegrationTest {

    private final ProductFacade productFacade;
    private final ProductJpaRepository productJpaRepository;
    private final StockJpaRepository stockJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductFacadeIntegrationTest(
        ProductFacade productFacade,
        ProductJpaRepository productJpaRepository,
        StockJpaRepository stockJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.productFacade = productFacade;
        this.productJpaRepository = productJpaRepository;
        this.stockJpaRepository = stockJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    class CreateProduct {

        @DisplayName("Product 와 Stock 이 같은 트랜잭션에서 함께 저장되고, 응답에 stock 이 포함된다.")
        @Test
        void persistsProductAndStockTogether() {
            // given
            String name = "에어맥스 270";
            String description = "가벼운 쿠셔닝의 데일리 러닝화";
            Long price = 159_000L;
            Integer stock = 50;

            // when
            ProductInfo result = productFacade.createProduct(name, description, price, stock);

            // then
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo(name),
                () -> assertThat(result.description()).isEqualTo(description),
                () -> assertThat(result.price()).isEqualTo(price),
                () -> assertThat(result.stock()).isEqualTo(stock),
                () -> assertThat(productJpaRepository.findById(result.id())).isPresent(),
                () -> assertThat(stockJpaRepository.findByProductId(result.id()))
                    .hasValueSatisfying(s -> assertThat(s.getQuantity()).isEqualTo(stock))
            );
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class UpdateProduct {

        @DisplayName("Product 정보와 Stock 의 quantity 가 새 값으로 갱신된다.")
        @Test
        void updatesProductFieldsAndStockQuantity() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50).id();
            String newName = "에어맥스 270 SE";
            String newDescription = "스페셜 에디션 컬러웨이";
            Long newPrice = 179_000L;
            Integer newStock = 80;

            // when
            ProductInfo result = productFacade.updateProduct(productId, newName, newDescription, newPrice, newStock);

            // then
            assertAll(
                () -> assertThat(result.name()).isEqualTo(newName),
                () -> assertThat(result.description()).isEqualTo(newDescription),
                () -> assertThat(result.price()).isEqualTo(newPrice),
                () -> assertThat(result.stock()).isEqualTo(newStock),
                () -> assertThat(stockJpaRepository.findByProductId(productId))
                    .hasValueSatisfying(s -> assertThat(s.getQuantity()).isEqualTo(newStock))
            );
        }

        @DisplayName("stock 을 더 작은 값으로 수정하면, Stock 의 quantity 가 감소한다.")
        @Test
        void decreasesStockQuantity_whenNewStockIsLess() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50).id();

            // when
            productFacade.updateProduct(productId, "에어맥스 270", "데일리 러닝화", 159_000L, 12);

            // then
            assertThat(stockJpaRepository.findByProductId(productId))
                .hasValueSatisfying(s -> assertThat(s.getQuantity()).isEqualTo(12));
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    class DeleteProduct {

        @DisplayName("Product 와 Stock 이 함께 사라진다.")
        @Test
        void deletesProductAndStockTogether() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50).id();

            // when
            productFacade.deleteProduct(productId);

            // then
            assertAll(
                () -> assertThat(productJpaRepository.findById(productId)).isEmpty(),
                () -> assertThat(stockJpaRepository.findByProductId(productId)).isEmpty()
            );
        }
    }
}
