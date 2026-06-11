package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductStockRepositoryIntegrationTest {

    private final ProductStockRepository productStockRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductStockRepositoryIntegrationTest(ProductStockRepository productStockRepository, DatabaseCleanUp databaseCleanUp) {
        this.productStockRepository = productStockRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("save 후 findByProductId 로 같은 stock 을 조회할 수 있다")
    void givenSavedStock_whenFindByProductId_thenReturnsStock() {
        productStockRepository.save(ProductStock.create(1L, 50));

        Optional<ProductStock> found = productStockRepository.findByProductId(1L);

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("존재하지 않는 productId 로 findByProductId 하면 빈 값을 반환한다")
    void givenNonExistingProductId_whenFindByProductId_thenReturnsEmpty() {
        Optional<ProductStock> found = productStockRepository.findByProductId(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("findByProductIdForUpdate 는 트랜잭션 안에서 동일 결과를 반환한다 (PESSIMISTIC_WRITE)")
    void givenSavedStock_whenFindByProductIdForUpdate_thenReturnsStock() {
        productStockRepository.save(ProductStock.create(1L, 50));

        Optional<ProductStock> found = productStockRepository.findByProductIdForUpdate(1L);

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(50);
    }
}
