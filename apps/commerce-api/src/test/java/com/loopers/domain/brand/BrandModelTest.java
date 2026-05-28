package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BrandModelTest {

    private static final String VALID_NAME = "나이키";
    private static final String VALID_DESCRIPTION = "스포츠 브랜드";

    @DisplayName("브랜드 생성")
    @Nested
    class Create {

        @DisplayName("유효한 name과 description이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrandModel_whenRequestIsValid() {
            // act
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // assert
            assertAll(
                () -> assertEquals(VALID_NAME, brand.getName()),
                () -> assertEquals(VALID_DESCRIPTION, brand.getDescription())
            );
        }

        @DisplayName("name이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(null, VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name이 빈 값이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel("", VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name 앞에 공백이 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameHasLeadingWhitespace() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(" " + VALID_NAME, VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name 뒤에 공백이 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNameHasTrailingWhitespace() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(VALID_NAME + " ", VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name이 1자이면, 정상적으로 생성된다. (BVA 하한)")
        @Test
        void createsBrandModel_whenNameIsOneChar() {
            // arrange
            String name = "나";

            // act
            BrandModel brand = new BrandModel(name, VALID_DESCRIPTION);

            // assert
            assertEquals(name, brand.getName());
        }

        @DisplayName("name이 정확히 100자이면, 정상적으로 생성된다. (BVA 상한)")
        @Test
        void createsBrandModel_whenNameIsExactly100Chars() {
            // arrange
            String name = "가".repeat(100);

            // act
            BrandModel brand = new BrandModel(name, VALID_DESCRIPTION);

            // assert
            assertEquals(name, brand.getName());
        }

        @DisplayName("name이 101자이면, 예외가 발생한다. (BVA 초과)")
        @Test
        void throwsException_whenNameExceeds100Chars() {
            // arrange
            String name = "가".repeat(101);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(name, VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("name이 공백 문자열이면, 예외가 발생한다. (Error Guessing)")
        @Test
        void throwsException_whenNameIsWhitespaceOnly() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel("   ", VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("description이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenDescriptionIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(VALID_NAME, null)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("description이 빈 값이면, 예외가 발생한다.")
        @Test
        void throwsException_whenDescriptionIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(VALID_NAME, "")
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("description이 1자이면, 정상적으로 생성된다. (BVA 하한)")
        @Test
        void createsBrandModel_whenDescriptionIsOneChar() {
            // arrange
            String description = "설";

            // act
            BrandModel brand = new BrandModel(VALID_NAME, description);

            // assert
            assertEquals(description, brand.getDescription());
        }

        @DisplayName("description이 정확히 500자이면, 정상적으로 생성된다. (BVA 상한)")
        @Test
        void createsBrandModel_whenDescriptionIsExactly500Chars() {
            // arrange
            String description = "설".repeat(500);

            // act
            BrandModel brand = new BrandModel(VALID_NAME, description);

            // assert
            assertEquals(description, brand.getDescription());
        }

        @DisplayName("description이 501자이면, 예외가 발생한다. (BVA 초과)")
        @Test
        void throwsException_whenDescriptionExceeds500Chars() {
            // arrange
            String description = "설".repeat(501);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(VALID_NAME, description)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }
    }

    @DisplayName("브랜드 수정")
    @Nested
    class Update {

        @DisplayName("유효한 name과 description이 주어지면, 정상적으로 수정된다.")
        @Test
        void updatesBrandModel_whenRequestIsValid() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);
            String newName = "아디다스";
            String newDescription = "독일 스포츠 브랜드";

            // act
            brand.update(newName, newDescription);

            // assert
            assertAll(
                () -> assertEquals(newName, brand.getName()),
                () -> assertEquals(newDescription, brand.getDescription())
            );
        }

        @DisplayName("새 name이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewNameIsNull() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update(null, VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name이 빈 값이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewNameIsEmpty() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update("", VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name 앞에 공백이 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewNameHasLeadingWhitespace() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update(" 아디다스", VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name 뒤에 공백이 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewNameHasTrailingWhitespace() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update("아디다스 ", VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name이 공백 문자열이면, 예외가 발생한다. (Error Guessing)")
        @Test
        void throwsException_whenNewNameIsWhitespaceOnly() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update("   ", VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 name이 정확히 100자이면, 정상적으로 수정된다. (BVA 상한)")
        @Test
        void updatesBrandModel_whenNewNameIsExactly100Chars() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);
            String newName = "가".repeat(100);

            // act
            brand.update(newName, VALID_DESCRIPTION);

            // assert
            assertEquals(newName, brand.getName());
        }

        @DisplayName("새 name이 101자이면, 예외가 발생한다. (BVA 초과)")
        @Test
        void throwsException_whenNewNameExceeds100Chars() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update("가".repeat(101), VALID_DESCRIPTION)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 description이 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewDescriptionIsNull() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update(VALID_NAME, null)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 description이 빈 값이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewDescriptionIsEmpty() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update(VALID_NAME, "")
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 description이 정확히 500자이면, 정상적으로 수정된다. (BVA 상한)")
        @Test
        void updatesBrandModel_whenNewDescriptionIsExactly500Chars() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);
            String newDescription = "설".repeat(500);

            // act
            brand.update(VALID_NAME, newDescription);

            // assert
            assertEquals(newDescription, brand.getDescription());
        }

        @DisplayName("새 description이 501자이면, 예외가 발생한다. (BVA 초과)")
        @Test
        void throwsException_whenNewDescriptionExceeds500Chars() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update(VALID_NAME, "설".repeat(501))
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }
    }
}
