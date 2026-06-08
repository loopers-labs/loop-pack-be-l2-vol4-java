package com.loopers.domain.product;

import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.product.vo.StockQuantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductStockModelTest {

    private static ProductStockModel stockOf(long price, int quantity) {
        ProductModel product = new ProductModel(1L, new ProductName("테스트상품"));
        return new ProductStockModel(product, new Price(price), quantity);
    }

    @DisplayName("가격 목표값 적용 시,")
    @Nested
    class ApplyPriceTo {

        @DisplayName("목표가가 현재보다 높으면, 가격이 증가한다.")
        @Test
        void increasesPrice_whenTargetIsHigher() {
            ProductStockModel stock = stockOf(10000L, 5);

            stock.applyPriceTo(15000L);

            assertThat(stock.getPrice().getValue()).isEqualTo(15000L);
        }

        @DisplayName("목표가가 현재보다 낮으면, 가격이 감소한다.")
        @Test
        void decreasesPrice_whenTargetIsLower() {
            ProductStockModel stock = stockOf(10000L, 5);

            stock.applyPriceTo(7000L);

            assertThat(stock.getPrice().getValue()).isEqualTo(7000L);
        }

        @DisplayName("목표가가 현재와 같으면, 가격이 변경되지 않는다.")
        @Test
        void doesNotChange_whenTargetEqualsCurrentPrice() {
            ProductStockModel stock = stockOf(10000L, 5);

            stock.applyPriceTo(10000L);

            assertThat(stock.getPrice().getValue()).isEqualTo(10000L);
        }
    }

    @DisplayName("재고 증감량 적용 시,")
    @Nested
    class ApplyQuantityDelta {

        @DisplayName("양수 증감량이면, 재고가 증가한다.")
        @Test
        void increasesQuantity_whenDeltaIsPositive() {
            ProductStockModel stock = stockOf(10000L, 5);

            stock.applyQuantityDelta(3);

            assertThat(stock.getStockQuantity().getValue()).isEqualTo(8);
        }

        @DisplayName("음수 증감량이면, 재고가 차감된다.")
        @Test
        void decreasesQuantity_whenDeltaIsNegative() {
            ProductStockModel stock = stockOf(10000L, 5);

            stock.applyQuantityDelta(-2);

            assertThat(stock.getStockQuantity().getValue()).isEqualTo(3);
        }

        @DisplayName("증감량이 0이면, 재고가 변경되지 않는다.")
        @Test
        void doesNotChange_whenDeltaIsZero() {
            ProductStockModel stock = stockOf(10000L, 5);

            stock.applyQuantityDelta(0);

            assertThat(stock.getStockQuantity().getValue()).isEqualTo(5);
        }

        @DisplayName("음수 증감량이 현재 재고를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDecreaseExceedsStock() {
            ProductStockModel stock = stockOf(10000L, 3);

            CoreException exception = assertThrows(CoreException.class,
                    () -> stock.applyQuantityDelta(-5));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }


    @DisplayName("Price -")
    @Nested
    class PriceTest {

        @DisplayName("가격 생성 시,")
        @Nested
        class Create {

            @DisplayName("0 이상이면, 정상 생성된다.")
            @Test
            void createsPrice_whenValueIsNonNegative() {
                assertThat(new Price(0L).getValue()).isEqualTo(0L);
                assertThat(new Price(10000L).getValue()).isEqualTo(10000L);
            }

            @DisplayName("음수이면, BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenValueIsNegative() {
                CoreException exception = assertThrows(CoreException.class, () -> new Price(-1L));

                assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("가격 증가 시,")
        @Nested
        class Increase {

            @DisplayName("유효한 증가량이면, 증가된 가격을 반환한다.")
            @Test
            void returnsIncreased_whenAmountIsValid() {
                Price price = new Price(10000L);

                Price result = price.increase(5000L);

                assertThat(result.getValue()).isEqualTo(15000L);
            }

            @DisplayName("증가량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenAmountIsNotPositive() {
                Price price = new Price(10000L);

                assertThrows(CoreException.class, () -> price.increase(0L));
                assertThrows(CoreException.class, () -> price.increase(-1L));
            }
        }

        @DisplayName("가격 감소 시,")
        @Nested
        class Decrease {

            @DisplayName("유효한 감소량이면, 감소된 가격을 반환한다.")
            @Test
            void returnsDecreased_whenAmountIsValid() {
                Price price = new Price(10000L);

                Price result = price.decrease(3000L);

                assertThat(result.getValue()).isEqualTo(7000L);
            }

            @DisplayName("감소량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenAmountIsNotPositive() {
                Price price = new Price(10000L);

                assertThrows(CoreException.class, () -> price.decrease(0L));
                assertThrows(CoreException.class, () -> price.decrease(-1L));
            }

            @DisplayName("감소량이 현재 가격을 초과하면, BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenAmountExceedsPrice() {
                Price price = new Price(10000L);

                CoreException exception = assertThrows(CoreException.class, () -> price.decrease(20000L));

                assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }
    }

    @DisplayName("StockQuantity -")
    @Nested
    class StockQuantityTest {

        @DisplayName("재고 차감 시,")
        @Nested
        class Decrease {

            @DisplayName("재고가 충분하면, 차감된 수량을 반환한다.")
            @Test
            void returnsDecreased_whenStockIsSufficient() {
                StockQuantity stock = new StockQuantity(10);

                StockQuantity result = stock.decrease(3);

                assertThat(result.getValue()).isEqualTo(7);
            }

            @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenStockIsInsufficient() {
                StockQuantity stock = new StockQuantity(2);

                CoreException exception = assertThrows(CoreException.class, () -> stock.decrease(5));

                assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("정확히 재고 수량만큼 차감하면, 0이 된다.")
            @Test
            void returnsZero_whenDecreaseEqualsStock() {
                StockQuantity stock = new StockQuantity(5);

                StockQuantity result = stock.decrease(5);

                assertThat(result.getValue()).isEqualTo(0);
            }
        }

        @DisplayName("재고 증가 시,")
        @Nested
        class Increase {

            @DisplayName("유효한 증가량이면, 증가된 수량을 반환한다.")
            @Test
            void returnsIncreased_whenAmountIsValid() {
                StockQuantity stock = new StockQuantity(5);

                StockQuantity result = stock.increase(3);

                assertThat(result.getValue()).isEqualTo(8);
            }
        }
    }
}
