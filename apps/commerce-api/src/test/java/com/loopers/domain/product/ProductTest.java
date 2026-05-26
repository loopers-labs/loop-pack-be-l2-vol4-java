package com.loopers.domain.product;

import com.loopers.domain.money.Money;
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

    @DisplayName("좋아요 수를 변경할 때, ")
    @Nested
    class LikeCount {
        @DisplayName("increaseLikeCount를 호출하면, likeCount가 1 증가한다.")
        @Test
        void increasesLikeCount_byOne() {
            // arrange
            Product product = new Product("에어맥스", "편한 러닝화",
                new Money(BigDecimal.valueOf(100000)), new Stock(10), 1L);

            // act
            product.increaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("decreaseLikeCount를 호출하면, likeCount가 1 감소한다.")
        @Test
        void decreasesLikeCount_byOne() {
            // arrange
            Product product = new Product("에어맥스", "편한 러닝화",
                new Money(BigDecimal.valueOf(100000)), new Stock(10), 1L);
            product.increaseLikeCount();

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0L);
        }

        @DisplayName("likeCount가 0일 때 decreaseLikeCount를 호출해도, 음수가 되지 않는다.")
        @Test
        void doesNotGoNegative_whenLikeCountIsZero() {
            // arrange
            Product product = new Product("에어맥스", "편한 러닝화",
                new Money(BigDecimal.valueOf(100000)), new Stock(10), 1L);

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0L);
        }
    }
}
