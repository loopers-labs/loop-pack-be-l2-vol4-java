package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    @DisplayName("ProductModel 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성되고 각 필드가 그대로 보관된다.")
        @Test
        void createsProductModel_whenAllFieldsAreValid() {
            // given
            String name = "에어맥스 270";
            String description = "가벼운 쿠셔닝의 데일리 러닝화";
            Long price = 159_000L;

            // when
            ProductModel product = new ProductModel(name, description, price);

            // then
            assertAll(
                () -> assertThat(product.getName()).isEqualTo(name),
                () -> assertThat(product.getDescription()).isEqualTo(description),
                () -> assertThat(product.getPrice()).isEqualTo(price)
            );
        }

        @DisplayName("name 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // given
            String name = null;
            String description = "가벼운 쿠셔닝의 데일리 러닝화";
            Long price = 159_000L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(name, description, price));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("name 이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // given
            String name = "  ";
            String description = "가벼운 쿠셔닝의 데일리 러닝화";
            Long price = 159_000L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(name, description, price));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("description 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDescriptionIsNull() {
            // given
            String name = "에어맥스 270";
            String description = null;
            Long price = 159_000L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(name, description, price));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 설명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("description 이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDescriptionIsBlank() {
            // given
            String name = "에어맥스 270";
            String description = "  ";
            Long price = 159_000L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(name, description, price));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 설명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("price 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNull() {
            // given
            String name = "에어맥스 270";
            String description = "가벼운 쿠셔닝의 데일리 러닝화";
            Long price = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(name, description, price));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("가격은 0 이상이어야 합니다.")
            );
        }

        @DisplayName("price 가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNegative() {
            // given
            String name = "에어맥스 270";
            String description = "가벼운 쿠셔닝의 데일리 러닝화";
            Long price = -1L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(name, description, price));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("가격은 0 이상이어야 합니다.")
            );
        }

        @DisplayName("생성 직후 likeCount 는 0 이다.")
        @Test
        void likeCountIsZero_whenJustCreated() {
            // given
            String name = "에어맥스 270";
            String description = "가벼운 쿠셔닝의 데일리 러닝화";
            Long price = 159_000L;

            // when
            ProductModel product = new ProductModel(name, description, price);

            // then
            assertThat(product.getLikeCount()).isEqualTo(0L);
        }

        @DisplayName("brandId 를 함께 받아 생성하면, brandId 가 그대로 보관된다.")
        @Test
        void keepsBrandId_whenCreatedWithBrandId() {
            // given
            String name = "슈퍼스타";
            String description = "쉘토 스니커즈의 상징";
            Long price = 129_000L;
            Long brandId = 42L;

            // when
            ProductModel product = new ProductModel(name, description, price, brandId);

            // then
            assertThat(product.getBrandId()).isEqualTo(brandId);
        }
    }

    @DisplayName("ProductModel 을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("모든 필드가 유효하면, 각 필드가 새 값으로 갱신된다.")
        @Test
        void updatesAllFields_whenAllFieldsAreValid() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);
            String newName = "에어맥스 270 SE";
            String newDescription = "스페셜 에디션 컬러웨이";
            Long newPrice = 179_000L;

            // when
            product.update(newName, newDescription, newPrice);

            // then
            assertAll(
                () -> assertThat(product.getName()).isEqualTo(newName),
                () -> assertThat(product.getDescription()).isEqualTo(newDescription),
                () -> assertThat(product.getPrice()).isEqualTo(newPrice)
            );
        }

        @DisplayName("name 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> product.update(null, "데일리 러닝화", 159_000L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("name 이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> product.update("  ", "데일리 러닝화", 159_000L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("description 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDescriptionIsNull() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> product.update("에어맥스 270", null, 159_000L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 설명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("description 이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDescriptionIsBlank() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> product.update("에어맥스 270", "  ", 159_000L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("상품 설명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("price 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNull() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> product.update("에어맥스 270", "데일리 러닝화", null));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("가격은 0 이상이어야 합니다.")
            );
        }

        @DisplayName("price 가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNegative() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> product.update("에어맥스 270", "데일리 러닝화", -1L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("가격은 0 이상이어야 합니다.")
            );
        }
    }

    @DisplayName("ProductModel 의 좋아요 수를 변경할 때, ")
    @Nested
    class LikeCount {

        @DisplayName("incrementLikeCount() 를 호출하면 likeCount 가 1 증가한다.")
        @Test
        void increasesLikeCountByOne_whenIncrementLikeCountIsCalled() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);

            // when
            product.incrementLikeCount();

            // then
            assertThat(product.getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("likeCount 가 1 일 때 decrementLikeCount() 를 호출하면 0 이 된다.")
        @Test
        void decreasesLikeCountByOne_whenDecrementLikeCountIsCalled() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);
            product.incrementLikeCount();

            // when
            product.decrementLikeCount();

            // then
            assertThat(product.getLikeCount()).isEqualTo(0L);
        }

        @DisplayName("likeCount 가 0 일 때 decrementLikeCount() 를 호출하면 INVALID_LIKE_COUNT 예외가 발생한다.")
        @Test
        void throwsInvalidLikeCountException_whenDecrementingFromZero() {
            // given
            ProductModel product = new ProductModel("에어맥스 270", "데일리 러닝화", 159_000L);

            // when
            CoreException result = assertThrows(CoreException.class, product::decrementLikeCount);

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_LIKE_COUNT),
                () -> assertThat(result.getCustomMessage()).isEqualTo("좋아요 수는 0 미만이 될 수 없습니다.")
            );
        }
    }
}
