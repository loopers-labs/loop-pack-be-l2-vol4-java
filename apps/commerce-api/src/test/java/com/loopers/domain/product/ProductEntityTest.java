package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProductEntityTest {

    private static final String VALID_BRAND_ID = "1";
    private static final String VALID_NAME = "에어맥스 90";
    private static final String VALID_DESCRIPTION = "나이키 클래식 러닝화";
    private static final Long VALID_PRICE = 150000L;

    @DisplayName("상품 생성")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsProductEntity_whenRequestIsValid() {
            // act
            ProductEntity product = new ProductEntity(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE);

            // assert
            assertAll(
                () -> assertEquals(VALID_BRAND_ID, product.getBrandId()),
                () -> assertEquals(VALID_NAME, product.getName()),
                () -> assertEquals(VALID_DESCRIPTION, product.getDescription()),
                () -> assertEquals(VALID_PRICE, product.getPrice()),
                () -> assertEquals(0L, product.getLikeCount())
            );
        }

        @DisplayName("brandId가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenBrandIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(null, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, null, VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name이 빈 값이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, "", VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name 앞에 공백이 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameHasLeadingWhitespace() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, " " + VALID_NAME, VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name 뒤에 공백이 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameHasTrailingWhitespace() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, VALID_NAME + " ", VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name이 공백 문자열이면, 예외가 발생한다. (Error Guessing)")
        @Test
        void throwsException_whenNameIsWhitespaceOnly() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, "   ", VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name이 1자이면, 정상적으로 생성된다. (BVA 하한)")
        @Test
        void createsProductEntity_whenNameIsOneChar() {
            // arrange
            String name = "신";

            // act
            ProductEntity product = new ProductEntity(VALID_BRAND_ID, name, VALID_DESCRIPTION, VALID_PRICE);

            // assert
            assertEquals(name, product.getName());
        }

        @DisplayName("name이 정확히 100자이면, 정상적으로 생성된다. (BVA 상한)")
        @Test
        void createsProductEntity_whenNameIsExactly100Chars() {
            // arrange
            String name = "가".repeat(100);

            // act
            ProductEntity product = new ProductEntity(VALID_BRAND_ID, name, VALID_DESCRIPTION, VALID_PRICE);

            // assert
            assertEquals(name, product.getName());
        }

        @DisplayName("name이 101자이면, 예외가 발생한다. (BVA 초과)")
        @Test
        void throwsException_whenNameExceeds100Chars() {
            // arrange
            String name = "가".repeat(101);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, name, VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("description이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenDescriptionIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, VALID_NAME, null, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("description이 빈 값이면, 예외가 발생한다.")
        @Test
        void throwsException_whenDescriptionIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, VALID_NAME, "", VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("description이 1자이면, 정상적으로 생성된다. (BVA 하한)")
        @Test
        void createsProductEntity_whenDescriptionIsOneChar() {
            // arrange
            String description = "설";

            // act
            ProductEntity product = new ProductEntity(VALID_BRAND_ID, VALID_NAME, description, VALID_PRICE);

            // assert
            assertEquals(description, product.getDescription());
        }

        @DisplayName("description이 정확히 500자이면, 정상적으로 생성된다. (BVA 상한)")
        @Test
        void createsProductEntity_whenDescriptionIsExactly500Chars() {
            // arrange
            String description = "설".repeat(500);

            // act
            ProductEntity product = new ProductEntity(VALID_BRAND_ID, VALID_NAME, description, VALID_PRICE);

            // assert
            assertEquals(description, product.getDescription());
        }

        @DisplayName("description이 501자이면, 예외가 발생한다. (BVA 초과)")
        @Test
        void throwsException_whenDescriptionExceeds500Chars() {
            // arrange
            String description = "설".repeat(501);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, VALID_NAME, description, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("price가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenPriceIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, null)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("price가 -1이면, 예외가 발생한다. (BVA 하한 미만)")
        @Test
        void throwsException_whenPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductEntity(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, -1L)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("price가 0이면, 정상적으로 생성된다. (BVA 하한)")
        @Test
        void createsProductEntity_whenPriceIsZero() {
            // act
            ProductEntity product = new ProductEntity(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, 0L);

            // assert
            assertEquals(0L, product.getPrice());
        }

        @DisplayName("price가 1이면, 정상적으로 생성된다. (BVA 하한+1)")
        @Test
        void createsProductEntity_whenPriceIsOne() {
            // act
            ProductEntity product = new ProductEntity(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, 1L);

            // assert
            assertEquals(1L, product.getPrice());
        }
    }

    @DisplayName("상품 수정")
    @Nested
    class Update {

        ProductEntity product;

        @BeforeEach
        void setup() {
            product = new ProductEntity(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE);
        }

        @DisplayName("유효한 값이 주어지면, 정상적으로 수정된다.")
        @Test
        void updatesProductEntity_whenRequestIsValid() {
            // arrange
            String newName = "에어포스 1";
            String newDescription = "나이키 농구화";
            Long newPrice = 120000L;

            // act
            product.update(newName, newDescription, newPrice);

            // assert
            assertAll(
                () -> assertEquals(newName, product.getName()),
                () -> assertEquals(newDescription, product.getDescription()),
                () -> assertEquals(newPrice, product.getPrice())
            );
        }

        @DisplayName("새 name이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update(null, VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name이 빈 값이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewNameIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update("", VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name 앞에 공백이 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewNameHasLeadingWhitespace() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update(" 에어포스", VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name 뒤에 공백이 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewNameHasTrailingWhitespace() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update("에어포스 ", VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name이 공백 문자열이면, 예외가 발생한다. (Error Guessing)")
        @Test
        void throwsException_whenNewNameIsWhitespaceOnly() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update("   ", VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name이 정확히 100자이면, 정상적으로 수정된다. (BVA 상한)")
        @Test
        void updatesProductEntity_whenNewNameIsExactly100Chars() {
            // arrange
            String newName = "가".repeat(100);

            // act
            product.update(newName, VALID_DESCRIPTION, VALID_PRICE);

            // assert
            assertEquals(newName, product.getName());
        }

        @DisplayName("새 name이 101자이면, 예외가 발생한다. (BVA 초과)")
        @Test
        void throwsException_whenNewNameExceeds100Chars() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update("가".repeat(101), VALID_DESCRIPTION, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 description이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewDescriptionIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update(VALID_NAME, null, VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 description이 빈 값이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewDescriptionIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update(VALID_NAME, "", VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 description이 정확히 500자이면, 정상적으로 수정된다. (BVA 상한)")
        @Test
        void updatesProductEntity_whenNewDescriptionIsExactly500Chars() {
            // arrange
            String newDescription = "설".repeat(500);

            // act
            product.update(VALID_NAME, newDescription, VALID_PRICE);

            // assert
            assertEquals(newDescription, product.getDescription());
        }

        @DisplayName("새 description이 501자이면, 예외가 발생한다. (BVA 초과)")
        @Test
        void throwsException_whenNewDescriptionExceeds500Chars() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update(VALID_NAME, "설".repeat(501), VALID_PRICE)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 price가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewPriceIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update(VALID_NAME, VALID_DESCRIPTION, null)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 price가 -1이면, 예외가 발생한다. (BVA 하한 미만)")
        @Test
        void throwsException_whenNewPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.update(VALID_NAME, VALID_DESCRIPTION, -1L)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 price가 0이면, 정상적으로 수정된다. (BVA 하한)")
        @Test
        void updatesProductEntity_whenNewPriceIsZero() {
            // act
            product.update(VALID_NAME, VALID_DESCRIPTION, 0L);

            // assert
            assertEquals(0L, product.getPrice());
        }

        @DisplayName("새 price가 1이면, 정상적으로 수정된다. (BVA 하한+1)")
        @Test
        void updatesProductEntity_whenNewPriceIsOne() {
            // act
            product.update(VALID_NAME, VALID_DESCRIPTION, 1L);

            // assert
            assertEquals(1L, product.getPrice());
        }
    }
}
