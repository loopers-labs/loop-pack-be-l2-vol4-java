package com.loopers.domain.product;

import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductModelTest {

    private static final Long VALID_BRAND_ID = 1L;
    private static final String VALID_NAME = "운동화";
    private static final String VALID_DESCRIPTION = "편안한 운동화";
    private static final Money VALID_PRICE = Money.of(50_000L);
    private static final Quantity VALID_STOCK = Quantity.of(10);
    private static final String VALID_IMAGE_URL = "https://example.com/image.png";

    private ProductModel newProduct() {
        return new ProductModel(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK, VALID_IMAGE_URL);
    }

    @DisplayName("정상 입력값으로 ProductModel 을 생성할 수 있다.")
    @Test
    void createsProductModel_withValidInput() {
        ProductModel product = newProduct();

        assertThat(product.getBrandId()).isEqualTo(VALID_BRAND_ID);
        assertThat(product.getName()).isEqualTo(VALID_NAME);
        assertThat(product.getPrice()).isEqualTo(VALID_PRICE);
        assertThat(product.getStockQuantity()).isEqualTo(VALID_STOCK);
        assertThat(product.getLikeCount()).isEqualTo(0);
    }

    @DisplayName("brandId 가 null 이면 BAD_REQUEST.")
    @Test
    void throwsBadRequest_whenBrandIdIsNull() {
        CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(null, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK, VALID_IMAGE_URL));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("상품명이 비어있으면 BAD_REQUEST.")
    @Test
    void throwsBadRequest_whenNameIsBlank() {
        CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(VALID_BRAND_ID, "  ", VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK, VALID_IMAGE_URL));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("상품명이 100자를 넘으면 BAD_REQUEST.")
    @Test
    void throwsBadRequest_whenNameExceedsMaxLength() {
        String tooLongName = "a".repeat(101);

        CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(VALID_BRAND_ID, tooLongName, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK, VALID_IMAGE_URL));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("재고를 차감할 수 있다.")
    @Test
    void decreaseStock_reducesStockQuantity() {
        ProductModel product = newProduct(); // stock = 10

        product.decreaseStock(Quantity.of(3));

        assertThat(product.getStockQuantity()).isEqualTo(Quantity.of(7));
    }

    @DisplayName("재고보다 많은 수량을 차감하려고 하면 BAD_REQUEST.")
    @Test
    void decreaseStock_throwsBadRequest_whenInsufficient() {
        ProductModel product = newProduct(); // stock = 10

        CoreException result = assertThrows(CoreException.class,
                () -> product.decreaseStock(Quantity.of(11)));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        // 차감 실패 시 재고 변하지 않음
        assertThat(product.getStockQuantity()).isEqualTo(Quantity.of(10));
    }

    @DisplayName("재고와 정확히 같은 수량을 차감하면 재고가 0 이 된다.")
    @Test
    void decreaseStock_resultsInZero_whenExactAmount() {
        ProductModel product = newProduct(); // stock = 10

        product.decreaseStock(Quantity.of(10));

        assertThat(product.getStockQuantity()).isEqualTo(Quantity.of(0));
        assertThat(product.isAvailable()).isFalse();
    }

    @DisplayName("재고를 복원할 수 있다.")
    @Test
    void restoreStock_increasesStockQuantity() {
        ProductModel product = newProduct(); // stock = 10
        product.decreaseStock(Quantity.of(3)); // stock = 7

        product.restoreStock(Quantity.of(3));

        assertThat(product.getStockQuantity()).isEqualTo(Quantity.of(10));
    }

    @DisplayName("좋아요 수를 증가/감소시킬 수 있다.")
    @Test
    void likeCount_canBeIncreasedAndDecreased() {
        ProductModel product = newProduct();

        product.increaseLikeCount();
        product.increaseLikeCount();
        assertThat(product.getLikeCount()).isEqualTo(2);

        product.decreaseLikeCount();
        assertThat(product.getLikeCount()).isEqualTo(1);
    }

    @DisplayName("좋아요 수는 0 미만으로 내려가지 않는다.")
    @Test
    void decreaseLikeCount_doesNotGoBelowZero() {
        ProductModel product = newProduct(); // likeCount = 0

        product.decreaseLikeCount();
        product.decreaseLikeCount();

        assertThat(product.getLikeCount()).isEqualTo(0);
    }

    @DisplayName("재고가 0 이면 isAvailable 은 false 다.")
    @Test
    void isAvailable_isFalse_whenStockIsZero() {
        ProductModel product = new ProductModel(
                VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, Quantity.of(0), VALID_IMAGE_URL
        );

        assertThat(product.isAvailable()).isFalse();
    }

    @DisplayName("재고가 1 이상이면 isAvailable 은 true 다.")
    @Test
    void isAvailable_isTrue_whenStockIsPositive() {
        ProductModel product = newProduct(); // stock = 10

        assertThat(product.isAvailable()).isTrue();
    }
}
