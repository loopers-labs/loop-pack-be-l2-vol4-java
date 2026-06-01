package com.loopers.stock.application;

import com.loopers.stock.infrastructure.StockJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class StockFacadeIntegrationTest {

    @Autowired
    private StockFacade stockFacade;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고를 생성할 때,")
    @Nested
    class CreateStock {

        @DisplayName("정상 요청이면, DB에 저장되고 StockInfo를 반환한다.")
        @Test
        void returnsStockInfo_whenRequestIsValid() {
            // act
            StockInfo result = stockFacade.createStock(1L, 100);

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.productId()).isEqualTo(1L),
                () -> assertThat(result.totalStock()).isEqualTo(100),
                () -> assertThat(result.reservedStock()).isEqualTo(0),
                () -> assertThat(result.availableStock()).isEqualTo(100)
            );
        }
    }

    @DisplayName("재고를 단건 조회할 때,")
    @Nested
    class GetStock {

        @DisplayName("존재하는 productId이면, StockInfo를 반환한다.")
        @Test
        void returnsStockInfo_whenStockExists() {
            // arrange
            stockFacade.createStock(1L, 50);

            // act
            StockInfo result = stockFacade.getStock(1L);

            // assert
            assertAll(
                () -> assertThat(result.productId()).isEqualTo(1L),
                () -> assertThat(result.availableStock()).isEqualTo(50)
            );
        }

        @DisplayName("존재하지 않는 productId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenStockNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                stockFacade.getStock(999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
