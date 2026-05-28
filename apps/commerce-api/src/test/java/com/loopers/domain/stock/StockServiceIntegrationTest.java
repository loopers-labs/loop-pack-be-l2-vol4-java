package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class StockServiceIntegrationTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private StockModel createStock(int totalQuantity) {
        return stockService.create(UUID.randomUUID(), totalQuantity);
    }

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 생성 시, totalQuantity가 저장되고 reservedQuantity = 0 이다.")
        @Test
        void savesStock_whenValidInput() {
            // act
            StockModel stock = createStock(100);

            // assert
            assertAll(
                () -> assertThat(stock.getId()).isNotNull(),
                () -> assertThat(stock.getTotalQuantity()).isEqualTo(100),
                () -> assertThat(stock.getReservedQuantity()).isZero()
            );
        }
    }

    @DisplayName("재고를 예약할 때,")
    @Nested
    class Reserve {

        @DisplayName("가용 재고 내 예약 시, reservedQuantity가 증가한다.")
        @Test
        void increasesReserved_whenSufficientStock() {
            // arrange
            StockModel stock = createStock(100);

            // act
            stockService.reserve(stock.getProductId(), 30);

            // assert
            StockModel updated = stockService.getByProductId(stock.getProductId());
            assertAll(
                () -> assertThat(updated.getReservedQuantity()).isEqualTo(30),
                () -> assertThat(updated.getAvailableQuantity()).isEqualTo(70)
            );
        }

        @DisplayName("가용 재고를 초과하여 예약 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenInsufficientStock() {
            // arrange
            StockModel stock = createStock(10);

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                stockService.reserve(stock.getProductId(), 11)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("결제를 확정할 때,")
    @Nested
    class Confirm {

        @DisplayName("confirm 호출 시, totalQuantity와 reservedQuantity가 함께 차감된다.")
        @Test
        void decreasesBothTotalAndReserved_whenConfirm() {
            // arrange
            StockModel stock = createStock(100);
            stockService.reserve(stock.getProductId(), 10);

            // act
            stockService.confirm(stock.getProductId(), 10);

            // assert
            StockModel updated = stockService.getByProductId(stock.getProductId());
            assertAll(
                () -> assertThat(updated.getTotalQuantity()).isEqualTo(90),
                () -> assertThat(updated.getReservedQuantity()).isZero()
            );
        }
    }

    @DisplayName("예약을 해제할 때,")
    @Nested
    class Release {

        @DisplayName("release 호출 시, reservedQuantity만 감소하고 totalQuantity는 유지된다.")
        @Test
        void releasesOnlyReserved_whenRelease() {
            // arrange
            StockModel stock = createStock(100);
            stockService.reserve(stock.getProductId(), 10);

            // act
            stockService.release(stock.getProductId(), 10);

            // assert
            StockModel updated = stockService.getByProductId(stock.getProductId());
            assertAll(
                () -> assertThat(updated.getTotalQuantity()).isEqualTo(100),
                () -> assertThat(updated.getReservedQuantity()).isZero()
            );
        }
    }

    @DisplayName("재고를 복구할 때,")
    @Nested
    class Restore {

        @DisplayName("restore 호출 시, totalQuantity가 증가한다.")
        @Test
        void restoresTotal_whenRestore() {
            // arrange
            StockModel stock = createStock(100);
            stockService.reserve(stock.getProductId(), 10);
            stockService.confirm(stock.getProductId(), 10);

            // act
            stockService.restore(stock.getProductId(), 10);

            // assert
            StockModel updated = stockService.getByProductId(stock.getProductId());
            assertThat(updated.getTotalQuantity()).isEqualTo(100);
        }
    }

    @DisplayName("어드민이 재고를 수정할 때,")
    @Nested
    class UpdateTotal {

        @DisplayName("유효한 값으로 수정 시, totalQuantity가 변경된다.")
        @Test
        void updatesTotal_whenValidInput() {
            // arrange
            StockModel stock = createStock(100);

            // act
            StockModel updated = stockService.updateTotal(stock.getProductId(), 200);

            // assert
            assertThat(updated.getTotalQuantity()).isEqualTo(200);
        }

        @DisplayName("예약 중인 수량 미만으로 수정 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNewTotalLessThanReserved() {
            // arrange
            StockModel stock = createStock(100);
            stockService.reserve(stock.getProductId(), 50);

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                stockService.updateTotal(stock.getProductId(), 30)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
