package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLineTest {

    @DisplayName("OrderLine 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성되고 각 필드가 그대로 보관된다.")
        @Test
        void createsOrderLine_whenAllFieldsAreValid() {
            // given
            Long productId = 100L;
            int quantity = 2;
            String productName = "에어맥스 270";
            Long productPrice = 159_000L;
            String brandName = "나이키";

            // when
            OrderLine line = new OrderLine(productId, quantity, productName, productPrice, brandName);

            // then
            assertAll(
                () -> assertThat(line.productId()).isEqualTo(productId),
                () -> assertThat(line.quantity()).isEqualTo(quantity),
                () -> assertThat(line.productName()).isEqualTo(productName),
                () -> assertThat(line.productPrice()).isEqualTo(productPrice),
                () -> assertThat(line.brandName()).isEqualTo(brandName)
            );
        }

        @DisplayName("productPrice 가 0 이어도, 정상적으로 생성된다.")
        @Test
        void createsOrderLine_whenProductPriceIsZero() {
            // given
            Long productPrice = 0L;

            // when
            OrderLine line = new OrderLine(100L, 1, "프로모션 사은품", productPrice, "아디다스");

            // then
            assertThat(line.productPrice()).isEqualTo(0L);
        }

        @DisplayName("productId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsNull() {
            // given
            Long productId = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(productId, 1, "에어맥스 270", 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 ID 는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("quantity 가 0 이면, INVALID_QUANTITY 예외가 발생한다.")
        @Test
        void throwsInvalidQuantityException_whenQuantityIsZero() {
            // given
            int quantity = 0;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, quantity, "에어맥스 270", 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY),
                () -> assertThat(result.getCustomMessage()).isEqualTo("주문 수량은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("quantity 가 음수이면, INVALID_QUANTITY 예외가 발생한다.")
        @Test
        void throwsInvalidQuantityException_whenQuantityIsNegative() {
            // given
            int quantity = -1;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, quantity, "에어맥스 270", 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY),
                () -> assertThat(result.getCustomMessage()).isEqualTo("주문 수량은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("productName 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductNameIsNull() {
            // given
            String productName = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, 1, productName, 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("productName 이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductNameIsEmpty() {
            // given
            String productName = "";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, 1, productName, 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("productName 이 공백 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductNameIsBlank() {
            // given
            String productName = "   ";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, 1, productName, 159_000L, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("productPrice 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductPriceIsNull() {
            // given
            Long productPrice = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, 1, "에어맥스 270", productPrice, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 가격 스냅샷은 0 이상이어야 합니다.")
            );
        }

        @DisplayName("productPrice 가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductPriceIsNegative() {
            // given
            Long productPrice = -1L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, 1, "에어맥스 270", productPrice, "나이키"));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 가격 스냅샷은 0 이상이어야 합니다.")
            );
        }

        @DisplayName("brandName 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandNameIsNull() {
            // given
            String brandName = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, 1, "에어맥스 270", 159_000L, brandName));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("brandName 이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandNameIsEmpty() {
            // given
            String brandName = "";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, 1, "에어맥스 270", 159_000L, brandName));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명 스냅샷은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("brandName 이 공백 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandNameIsBlank() {
            // given
            String brandName = "   ";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderLine(100L, 1, "에어맥스 270", 159_000L, brandName));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명 스냅샷은 비어있을 수 없습니다.")
            );
        }
    }

    @DisplayName("Product/Brand 로부터 스냅샷을 생성할 때, ")
    @Nested
    class SnapshotOf {

        @DisplayName("정상 인자가 주어지면, productId/quantity 와 product·brand 의 이름·가격이 그대로 스냅샷으로 보관된다.")
        @Test
        void createsSnapshotFromProductAndBrand_whenAllArgumentsAreValid() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L, 1L);
            ReflectionTestUtils.setField(product, "id", 100L);
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            int quantity = 2;

            // when
            OrderLine line = OrderLine.snapshotOf(product, brand, quantity);

            // then
            assertAll(
                () -> assertThat(line.productId()).isEqualTo(100L),
                () -> assertThat(line.quantity()).isEqualTo(quantity),
                () -> assertThat(line.productName()).isEqualTo("에어맥스 270"),
                () -> assertThat(line.productPrice()).isEqualTo(159_000L),
                () -> assertThat(line.brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("quantity 가 0 이면, OrderLine 의 불변식이 작동해 INVALID_QUANTITY 예외가 발생한다.")
        @Test
        void throwsInvalidQuantity_whenQuantityIsZero() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L, 1L);
            ReflectionTestUtils.setField(product, "id", 100L);
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            int quantity = 0;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> OrderLine.snapshotOf(product, brand, quantity));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
        }

        @DisplayName("스냅샷 생성 후 product 의 이름·가격이 바뀌어도, 이미 만들어진 OrderLine 의 값은 변하지 않는다.")
        @Test
        void keepsSnapshotIsolated_whenProductIsMutatedAfterSnapshot() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L, 1L);
            ReflectionTestUtils.setField(product, "id", 100L);
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            OrderLine line = OrderLine.snapshotOf(product, brand, 1);

            // when
            product.update("에어맥스 90 (개편)", "신규 컬러", 200_000L);

            // then
            assertAll(
                () -> assertThat(line.productName()).isEqualTo("에어맥스 270"),
                () -> assertThat(line.productPrice()).isEqualTo(159_000L)
            );
        }
    }
}
