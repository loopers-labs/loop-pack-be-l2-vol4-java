package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductTest {

    private static final Long BRAND_ID = 1L;
    private static final String NAME = "스트라이프 셔츠";
    private static final String DESCRIPTION = "면 100% 소재의 캐주얼 셔츠";
    private static final long PRICE = 29_000L;

    @Test
    @DisplayName("create 로 생성하면 모든 필드가 저장된다")
    void givenValidFields_whenCreate_thenStoresAllFields() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE);

        assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(BRAND_ID),
                () -> assertThat(product.getName()).isEqualTo(NAME),
                () -> assertThat(product.getDescription()).isEqualTo(DESCRIPTION),
                () -> assertThat(product.getPrice()).isEqualTo(PRICE)
        );
    }

    @Test
    @DisplayName("description 은 null 이어도 생성된다")
    void givenNullDescription_whenCreate_thenStoresNull() {
        Product product = Product.create(BRAND_ID, NAME, null, PRICE);

        assertThat(product.getDescription()).isNull();
    }

    @Test
    @DisplayName("price 가 0 이어도 생성된다 (사은품/이벤트 허용)")
    void givenZeroPrice_whenCreate_thenStoresZero() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, 0L);

        assertThat(product.getPrice()).isZero();
    }

    @Test
    @DisplayName("brandId 가 null 이면 CoreException 이 발생한다")
    void givenNullBrandId_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Product.create(null, NAME, DESCRIPTION, PRICE))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("brandId 는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("이름이 비어있으면 CoreException 이 발생한다")
    void givenBlankName_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> Product.create(BRAND_ID, invalid, DESCRIPTION, PRICE))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품 이름은 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -100L})
    @DisplayName("price 가 음수이면 CoreException 이 발생한다")
    void givenNegativePrice_whenCreate_thenThrowsCoreException(long invalid) {
        assertThatThrownBy(() -> Product.create(BRAND_ID, NAME, DESCRIPTION, invalid))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("가격은 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("update 로 이름과 설명과 가격을 변경할 수 있다")
    void givenProduct_whenUpdate_thenChangesNameDescriptionAndPrice() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE);

        product.update("프린트 셔츠", "프린트 패턴 셔츠", 35_000L);

        assertAll(
                () -> assertThat(product.getName()).isEqualTo("프린트 셔츠"),
                () -> assertThat(product.getDescription()).isEqualTo("프린트 패턴 셔츠"),
                () -> assertThat(product.getPrice()).isEqualTo(35_000L)
        );
    }

    @Test
    @DisplayName("update 후에도 brandId 는 변경되지 않는다 (수정 대상 아님)")
    void givenProduct_whenUpdate_thenBrandIdIsUnchanged() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE);

        product.update("프린트 셔츠", "설명", 35_000L);

        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
    }

    @Test
    @DisplayName("update 에서 description 을 null 로 변경할 수 있다")
    void givenProduct_whenUpdateWithNullDescription_thenDescriptionBecomesNull() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE);

        product.update(NAME, null, PRICE);

        assertThat(product.getDescription()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("update 시 이름이 비어있으면 CoreException 이 발생한다")
    void givenProduct_whenUpdateWithBlankName_thenThrowsCoreException(String invalid) {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE);

        assertThatThrownBy(() -> product.update(invalid, DESCRIPTION, PRICE))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품 이름은 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -100L})
    @DisplayName("update 시 price 가 음수이면 CoreException 이 발생한다")
    void givenProduct_whenUpdateWithNegativePrice_thenThrowsCoreException(long invalid) {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE);

        assertThatThrownBy(() -> product.update(NAME, DESCRIPTION, invalid))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("가격은 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("delete 호출 시 deletedAt 이 채워진다")
    void givenProduct_whenDelete_thenDeletedAtIsSet() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE);

        product.delete();

        assertThat(product.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete 는 멱등하다")
    void givenDeletedProduct_whenDeleteAgain_thenDeletedAtRemainsUnchanged() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE);

        product.delete();
        var firstDeletedAt = product.getDeletedAt();
        product.delete();

        assertThat(product.getDeletedAt()).isEqualTo(firstDeletedAt);
    }
}
