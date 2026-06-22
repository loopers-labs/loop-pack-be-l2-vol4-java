package com.loopers.domain.stock;

import com.loopers.application.stock.StockService;
import com.loopers.infrastructure.stock.StockJpaRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StockServiceIntegrationTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Decrease {

        @DisplayName("차감 결과가 DB에 실제로 반영된다.")
        @Test
        void decreasesQuantity_atDbLevel() {
            // arrange
            StockModel stock = stockJpaRepository.save(new StockModel(1L, 10));

            // act
            stockService.decrease(stock.getProductId(), 3);

            // assert
            StockModel updated = stockJpaRepository.findByProductId(stock.getProductId()).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(7);
        }
    }

    @DisplayName("재고를 증가할 때,")
    @Nested
    class Increase {

        @DisplayName("증가 결과가 DB에 실제로 반영된다.")
        @Test
        void increasesQuantity_atDbLevel() {
            // arrange
            StockModel stock = stockJpaRepository.save(new StockModel(1L, 10));

            // act
            stockService.increase(stock.getProductId(), 5);

            // assert
            StockModel updated = stockJpaRepository.findByProductId(stock.getProductId()).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(15);
        }
    }

    @DisplayName("soft delete된 재고를 조회할 때,")
    @Nested
    class SoftDelete {

        @DisplayName("삭제된 재고는 조회되지 않는다.")
        @Test
        void throwsNotFound_whenStockIsSoftDeleted() {
            // arrange
            StockModel stock = stockJpaRepository.save(new StockModel(1L, 10));
            stock.delete();
            stockJpaRepository.save(stock);

            // act & assert
            assertThatThrownBy(() -> stockService.getByProductId(stock.getProductId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
