package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.loopers.fixture.ProductModelFixture.aProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductModelTest {

    @Nested
    @DisplayName("ProductModel 생성")
    class Create {

        @DisplayName("유효한 값으로 생성하면, 좋아요 0·활성 상태의 Product가 만들어진다")
        @Test
        void given_validInput_when_create_then_createsActiveProductWithZeroLikes() {
            ProductModel product = new ProductModel(1L, "에어맥스 90", "클래식 러닝화",
                    "http://img/airmax.png", 139000L, 10);

            assertAll(
                    () -> assertThat(product.getBrandId()).isEqualTo(1L),
                    () -> assertThat(product.getName()).isEqualTo("에어맥스 90"),
                    () -> assertThat(product.getPrice()).isEqualTo(139000L),
                    () -> assertThat(product.getStock()).isEqualTo(10),
                    () -> assertThat(product.getLikesCount()).isEqualTo(0L),
                    () -> assertThat(product.isActive()).isTrue()
            );
        }

        @DisplayName("brandId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullBrandId_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class,
                    () -> aProduct().withBrandId(null).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        void given_nullOrBlankName_when_create_then_throwsBadRequest(String invalidName) {
            CoreException result = assertThrows(CoreException.class,
                    () -> aProduct().withName(invalidName).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 200자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nameOverMaxLength_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class,
                    () -> aProduct().withName("가".repeat(201)).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명·이미지URL은 null이어도 정상 생성된다 (nullable)")
        @Test
        void given_nullDescriptionAndImageUrl_when_create_then_creates() {
            ProductModel product = aProduct().withDescription(null).withImageUrl(null).build();

            assertAll(
                    () -> assertThat(product.getDescription()).isNull(),
                    () -> assertThat(product.getImageUrl()).isNull()
            );
        }

        @DisplayName("가격이 null이거나 음수면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(longs = {-1L, -1000L})
        void given_negativePrice_when_create_then_throwsBadRequest(Long invalidPrice) {
            CoreException result = assertThrows(CoreException.class,
                    () -> aProduct().withPrice(invalidPrice).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 null이거나 음수면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {-1, -100})
        void given_negativeStock_when_create_then_throwsBadRequest(Integer invalidStock) {
            CoreException result = assertThrows(CoreException.class,
                    () -> aProduct().withStock(invalidStock).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("재고 차감 (deductStock)")
    class DeductStock {

        @DisplayName("재고가 충분하면 차감된다")
        @Test
        void given_enoughStock_when_deduct_then_reduced() {
            ProductModel product = aProduct().withStock(10).build();

            product.deductStock(3);

            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("재고와 동일한 수량을 차감하면 0이 된다 (경계)")
        @Test
        void given_exactStock_when_deduct_then_zero() {
            ProductModel product = aProduct().withStock(5).build();

            product.deductStock(5);

            assertThat(product.getStock()).isEqualTo(0);
        }

        @DisplayName("재고보다 많은 수량을 차감하면 CONFLICT 예외가 발생한다")
        @Test
        void given_insufficientStock_when_deduct_then_throwsConflict() {
            ProductModel product = aProduct().withStock(2).build();

            CoreException result = assertThrows(CoreException.class, () -> product.deductStock(3));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("차감 수량이 0 이하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void given_nonPositiveQuantity_when_deduct_then_throwsBadRequest(Integer quantity) {
            ProductModel product = aProduct().withStock(10).build();

            CoreException result = assertThrows(CoreException.class, () -> product.deductStock(quantity));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("재고 복원 (restoreStock)")
    class RestoreStock {

        @DisplayName("복원하면 재고가 증가한다")
        @Test
        void given_quantity_when_restore_then_increased() {
            ProductModel product = aProduct().withStock(5).build();

            product.restoreStock(3);

            assertThat(product.getStock()).isEqualTo(8);
        }

        @DisplayName("복원 수량이 0 이하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void given_nonPositiveQuantity_when_restore_then_throwsBadRequest(Integer quantity) {
            ProductModel product = aProduct().withStock(5).build();

            CoreException result = assertThrows(CoreException.class, () -> product.restoreStock(quantity));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("좋아요 카운터")
    class LikesCount {

        @DisplayName("증가하면 likesCount가 1 늘어난다")
        @Test
        void given_product_when_increment_then_plusOne() {
            ProductModel product = aProduct().build();

            product.incrementLikesCount();

            assertThat(product.getLikesCount()).isEqualTo(1L);
        }

        @DisplayName("감소하면 likesCount가 1 줄어든다")
        @Test
        void given_likedProduct_when_decrement_then_minusOne() {
            ProductModel product = aProduct().build();
            product.incrementLikesCount();
            product.incrementLikesCount();

            product.decrementLikesCount();

            assertThat(product.getLikesCount()).isEqualTo(1L);
        }

        @DisplayName("0에서 감소해도 음수가 되지 않고 0을 유지한다 (음수 방지)")
        @Test
        void given_zeroLikes_when_decrement_then_staysZero() {
            ProductModel product = aProduct().build();

            product.decrementLikesCount();

            assertThat(product.getLikesCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @DisplayName("delete() 하면 비활성이 된다")
        @Test
        void given_activeProduct_when_delete_then_inactive() {
            ProductModel product = aProduct().build();

            product.delete();

            assertThat(product.isActive()).isFalse();
        }
    }
}
