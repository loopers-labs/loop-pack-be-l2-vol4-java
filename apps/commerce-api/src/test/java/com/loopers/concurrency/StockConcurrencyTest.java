package com.loopers.concurrency;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderCreationService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 동시성 테스트.
 * <p>
 * 재고 50 인 상품에 100 명이 동시에 1 개씩 주문 → 정확히 50 명만 성공하고 50 명은 CONFLICT.
 * <p>
 * 검증 대상: ProductRepository.findForUpdate (PESSIMISTIC_WRITE) + ProductModel.decreaseStock.
 */
class StockConcurrencyTest extends AbstractH2ConcurrencyTest {

    @Autowired
    private OrderCreationService orderCreationService;
    @Autowired
    private ProductService productService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private com.loopers.utils.DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 50 인 상품에 100 명이 동시에 1 개씩 주문하면, 정확히 50 건만 성공하고 재고는 0 이 된다.")
    @Test
    void stockExactlyConsumed() throws InterruptedException {
        // arrange
        Long brandId = brandService.createBrand("나이키", "스포츠").getId();
        ProductModel product = productService.createProduct(brandId, "에어맥스", "런닝화", 1000L, 50, null);
        long productId = product.getId();
        int threadCount = 100;

        // act
        ConcurrencyTestSupport.Result result = ConcurrencyTestSupport.runRace(threadCount, () -> {
            long userId = Thread.currentThread().getId() + 2_000_000L;
            orderCreationService.create(userId, List.of(new OrderLine(productId, 1)), null);
        });

        // assert
        ProductModel refreshed = productRepository.find(productId).orElseThrow();
        assertThat(result.success()).isEqualTo(50);
        assertThat(result.failure()).isEqualTo(50);
        assertThat(refreshed.getStock()).isZero();
    }
}
