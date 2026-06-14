package com.loopers.domain.product;

import com.loopers.domain.money.Money;
import com.loopers.domain.quantity.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {
    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("필수 정보가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenRequiredInfoIsProvided() {
            // arrange
            String name = "에어맥스";
            String description = "편한 러닝화";
            Money price = new Money(BigDecimal.valueOf(100000));
            Stock stock = new Stock(10);
            Long brandId = 1L;

            // act
            Product product = new Product(name, description, price, stock, brandId);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo(name),
                () -> assertThat(product.getDescription()).isEqualTo(description),
                () -> assertThat(product.getPrice()).isEqualTo(price),
                () -> assertThat(product.getStock()).isEqualTo(stock),
                () -> assertThat(product.getBrandId()).isEqualTo(brandId),
                () -> assertThat(product.getLikeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String name = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Product(name, "편한 러닝화", new Money(BigDecimal.valueOf(100000)), new Stock(10), 1L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DecreaseStock {
        @DisplayName("주어진 수량만큼 재고가 차감된다.")
        @Test
        void decreasesStock_byGivenQuantity() {
            // arrange
            Product product = new Product("에어맥스", "편한 러닝화",
                new Money(BigDecimal.valueOf(100000)), new Stock(10), 1L);
            Quantity quantity = new Quantity(3);

            // act
            product.decreaseStock(quantity);

            // assert
            assertThat(product.getStock().getQuantity()).isEqualTo(7);
        }
    }
}
