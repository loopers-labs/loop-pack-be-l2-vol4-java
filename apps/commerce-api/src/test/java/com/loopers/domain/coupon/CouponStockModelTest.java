package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponStockModelTest {

    @DisplayName("재고를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 수량이 주어지면, issued=0으로 생성된다.")
        @Test
        void createsStock_whenValid() {
            CouponStockModel stock = new CouponStockModel(1L, 100);

            assertThat(stock.getQuota()).isEqualTo(100);
            assertThat(stock.getIssued()).isZero();
            assertThat(stock.isSoldOut()).isFalse();
        }

        @DisplayName("수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_whenQuotaNotPositive() {
            CoreException ex = assertThrows(CoreException.class, () -> new CouponStockModel(1L, 0));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("발급(issue)할 때, ")
    @Nested
    class Issue {

        @DisplayName("수량이 남아있으면, issued가 1 증가한다.")
        @Test
        void increasesIssued_whenAvailable() {
            CouponStockModel stock = new CouponStockModel(1L, 2);

            stock.issue();

            assertThat(stock.getIssued()).isEqualTo(1);
            assertThat(stock.isSoldOut()).isFalse();
        }

        @DisplayName("수량만큼 발급하면, 소진 상태가 된다.")
        @Test
        void becomesSoldOut_whenIssuedUpToQuota() {
            CouponStockModel stock = new CouponStockModel(1L, 2);

            stock.issue();
            stock.issue();

            assertThat(stock.getIssued()).isEqualTo(2);
            assertThat(stock.isSoldOut()).isTrue();
        }

        @DisplayName("이미 소진된 뒤 발급하면, CONFLICT 예외가 발생한다.")
        @Test
        void throws_whenAlreadySoldOut() {
            CouponStockModel stock = new CouponStockModel(1L, 1);
            stock.issue();

            CoreException ex = assertThrows(CoreException.class, stock::issue);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
