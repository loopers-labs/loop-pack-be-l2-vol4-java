package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockModelTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 초기 수량으로 생성 시, reservedQuantity = 0 으로 초기화된다.")
        @Test
        void initializesReservedToZero_whenValidInput() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);

            assertAll(
                () -> assertThat(stock.getTotalQuantity()).isEqualTo(100),
                () -> assertThat(stock.getReservedQuantity()).isZero(),
                () -> assertThat(stock.getAvailableQuantity()).isEqualTo(100)
            );
        }

        @DisplayName("초기 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalQuantityIsNegative() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new StockModel(PRODUCT_ID, -1)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 예약할 때,")
    @Nested
    class Reserve {

        @DisplayName("가용 재고 이하로 예약 시, reservedQuantity가 증가한다.")
        @Test
        void increasesReserved_whenValidQty() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);

            stock.reserve(30);

            assertAll(
                () -> assertThat(stock.getReservedQuantity()).isEqualTo(30),
                () -> assertThat(stock.getAvailableQuantity()).isEqualTo(70)
            );
        }

        @DisplayName("예약 수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQtyIsZeroOrNegative() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);

            CoreException ex = assertThrows(CoreException.class, () -> stock.reserve(0));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가용 재고를 초과하여 예약 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenInsufficientStock() {
            StockModel stock = new StockModel(PRODUCT_ID, 10);

            CoreException ex = assertThrows(CoreException.class, () -> stock.reserve(11));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("결제를 확정할 때,")
    @Nested
    class Confirm {

        @DisplayName("confirm 호출 시, totalQuantity와 reservedQuantity 모두 차감된다.")
        @Test
        void decreasesBothTotalAndReserved_whenConfirm() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);
            stock.reserve(10);

            stock.confirm(10);

            assertAll(
                () -> assertThat(stock.getTotalQuantity()).isEqualTo(90),
                () -> assertThat(stock.getReservedQuantity()).isZero()
            );
        }

        @DisplayName("확정 수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQtyIsZeroOrNegative() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);
            stock.reserve(10);

            CoreException ex = assertThrows(CoreException.class, () -> stock.confirm(0));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("예약 수량보다 많이 확정하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenQtyExceedsReserved() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);
            stock.reserve(5);

            CoreException ex = assertThrows(CoreException.class, () -> stock.confirm(6));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("예약을 해제할 때,")
    @Nested
    class Release {

        @DisplayName("release 호출 시, reservedQuantity만 감소하고 totalQuantity는 유지된다.")
        @Test
        void decreasesOnlyReserved_whenRelease() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);
            stock.reserve(10);

            stock.release(10);

            assertAll(
                () -> assertThat(stock.getTotalQuantity()).isEqualTo(100),
                () -> assertThat(stock.getReservedQuantity()).isZero(),
                () -> assertThat(stock.getAvailableQuantity()).isEqualTo(100)
            );
        }

        @DisplayName("해제 수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQtyIsZeroOrNegative() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);
            stock.reserve(10);

            CoreException ex = assertThrows(CoreException.class, () -> stock.release(0));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("예약 수량보다 많이 해제하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenQtyExceedsReserved() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);
            stock.reserve(5);

            CoreException ex = assertThrows(CoreException.class, () -> stock.release(6));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("재고를 복구할 때,")
    @Nested
    class Restore {

        @DisplayName("restore 호출 시, totalQuantity가 증가한다.")
        @Test
        void increasesTotal_whenRestore() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);
            stock.reserve(10);
            stock.confirm(10);

            stock.restore(10);

            assertThat(stock.getTotalQuantity()).isEqualTo(100);
        }

        @DisplayName("복구 수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQtyIsZeroOrNegative() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);

            CoreException ex = assertThrows(CoreException.class, () -> stock.restore(0));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("어드민이 재고를 수정할 때,")
    @Nested
    class UpdateTotal {

        @DisplayName("유효한 값으로 수정 시, totalQuantity가 변경된다.")
        @Test
        void updatesTotal_whenValidInput() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);

            stock.updateTotal(200);

            assertThat(stock.getTotalQuantity()).isEqualTo(200);
        }

        @DisplayName("예약 중인 수량 미만으로 수정 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNewTotalLessThanReserved() {
            StockModel stock = new StockModel(PRODUCT_ID, 100);
            stock.reserve(50);

            CoreException ex = assertThrows(CoreException.class, () -> stock.updateTotal(30));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
