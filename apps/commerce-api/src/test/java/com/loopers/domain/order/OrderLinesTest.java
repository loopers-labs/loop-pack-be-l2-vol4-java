package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLinesTest {

    @DisplayName("OrderLines 를 생성할 때, ")
    @Nested
    class Of {

        @DisplayName("모든 라인이 서로 다른 productId 면, 합산 없이 입력 순서대로 보관된다.")
        @Test
        void preservesLines_whenAllProductIdsAreDistinct() {
            // given
            OrderLine first = new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키");
            OrderLine second = new OrderLine(200L, 2, "스탠스미스", 50_000L, "아디다스");
            List<OrderLine> raw = List.of(first, second);

            // when
            OrderLines lines = OrderLines.of(raw);

            // then
            assertThat(lines.values()).containsExactly(first, second);
        }

        @DisplayName("같은 productId 가 두 번 들어오면, 한 라인으로 합쳐지고 quantity 가 합산된다.")
        @Test
        void mergesQuantity_whenSameProductIdAppearsTwice() {
            // given
            List<OrderLine> raw = List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(100L, 3, "에어맥스 270", 100_000L, "나이키")
            );

            // when
            OrderLines lines = OrderLines.of(raw);

            // then
            assertAll(
                () -> assertThat(lines.values()).hasSize(1),
                () -> assertThat(lines.values().get(0).productId()).isEqualTo(100L),
                () -> assertThat(lines.values().get(0).quantity()).isEqualTo(5)
            );
        }

        @DisplayName("같은 productId 가 세 번 이상 들어와도, 모두 한 라인으로 합산된다.")
        @Test
        void mergesQuantity_whenSameProductIdAppearsMultipleTimes() {
            // given
            List<OrderLine> raw = List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(100L, 4, "에어맥스 270", 100_000L, "나이키")
            );

            // when
            OrderLines lines = OrderLines.of(raw);

            // then
            assertAll(
                () -> assertThat(lines.values()).hasSize(1),
                () -> assertThat(lines.values().get(0).quantity()).isEqualTo(7)
            );
        }

        @DisplayName("여러 productId 가 섞여 있고 일부가 중복되면, 중복은 합산되고 첫 등장 순서가 유지된다.")
        @Test
        void preservesFirstAppearanceOrder_whenMixedDuplicates() {
            // given
            List<OrderLine> raw = List.of(
                new OrderLine(200L, 1, "스탠스미스", 50_000L, "아디다스"),
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(200L, 3, "스탠스미스", 50_000L, "아디다스")
            );

            // when
            OrderLines lines = OrderLines.of(raw);

            // then
            assertAll(
                () -> assertThat(lines.values()).hasSize(2),
                () -> assertThat(lines.values().get(0).productId()).isEqualTo(200L),
                () -> assertThat(lines.values().get(0).quantity()).isEqualTo(4),
                () -> assertThat(lines.values().get(1).productId()).isEqualTo(100L),
                () -> assertThat(lines.values().get(1).quantity()).isEqualTo(2)
            );
        }

        @DisplayName("같은 productId 의 첫 라인 스냅샷(name/price/brand)이 합산 결과에 그대로 유지된다.")
        @Test
        void keepsFirstSnapshot_whenMergingSameProductId() {
            // given
            List<OrderLine> raw = List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키")
            );

            // when
            OrderLines lines = OrderLines.of(raw);

            // then
            OrderLine merged = lines.values().get(0);
            assertAll(
                () -> assertThat(merged.productName()).isEqualTo("에어맥스 270"),
                () -> assertThat(merged.productPrice()).isEqualTo(100_000L),
                () -> assertThat(merged.brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("빈 리스트가 들어오면, 빈 OrderLines 가 생성된다.")
        @Test
        void createsEmptyOrderLines_whenInputIsEmpty() {
            // given
            List<OrderLine> raw = List.of();

            // when
            OrderLines lines = OrderLines.of(raw);

            // then
            assertAll(
                () -> assertThat(lines.values()).isEmpty(),
                () -> assertThat(lines.isEmpty()).isTrue()
            );
        }

        @DisplayName("null 이 들어오면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenInputIsNull() {
            // given
            List<OrderLine> raw = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderLines.of(raw));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("values() 가 반환한 리스트는 수정할 수 없다 (불변).")
        @Test
        void returnsUnmodifiableList_fromValues() {
            // given
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키")
            ));

            // when & then
            assertThrows(UnsupportedOperationException.class,
                () -> lines.values().add(new OrderLine(200L, 1, "스탠스미스", 50_000L, "아디다스")));
        }
    }

    @DisplayName("totalAmount() 는, ")
    @Nested
    class TotalAmount {

        @DisplayName("단일 라인이면, price × quantity 를 반환한다.")
        @Test
        void returnsPriceTimesQuantity_whenSingleLine() {
            // given
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키")
            ));

            // when
            Long total = lines.totalAmount();

            // then
            assertThat(total).isEqualTo(200_000L);
        }

        @DisplayName("여러 라인이면, 각 라인의 price × quantity 의 합을 반환한다.")
        @Test
        void returnsSumOfSubtotals_whenMultipleLines() {
            // given
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(200L, 3, "스탠스미스", 50_000L, "아디다스")
            ));

            // when
            Long total = lines.totalAmount();

            // then
            assertThat(total).isEqualTo(100_000L + 50_000L * 3L);
        }

        @DisplayName("같은 productId 가 합산된 후의 quantity 로 계산된다.")
        @Test
        void usesMergedQuantity_whenSameProductIdAppearsTwice() {
            // given
            OrderLines lines = OrderLines.of(List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(100L, 3, "에어맥스 270", 100_000L, "나이키")
            ));

            // when
            Long total = lines.totalAmount();

            // then
            assertThat(total).isEqualTo(100_000L * 5L);
        }

        @DisplayName("빈 OrderLines 면, 0 을 반환한다.")
        @Test
        void returnsZero_whenEmpty() {
            // given
            OrderLines lines = OrderLines.of(List.of());

            // when
            Long total = lines.totalAmount();

            // then
            assertThat(total).isEqualTo(0L);
        }
    }
}
