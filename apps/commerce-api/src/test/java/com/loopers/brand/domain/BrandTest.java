package com.loopers.brand.domain;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class BrandTest {

    private static final String NAME = "루퍼스";
    private static final String DESCRIPTION = "트렌디한 라이프스타일 브랜드";
    private static final String LOGO_URL = "https://cdn.loopers.com/brands/loopers.png";

    @Test
    @DisplayName("create 로 생성하면 이름, 설명, 로고가 저장된다")
    void givenValidFields_whenCreate_thenStoresAllFields() {
        Brand brand = Brand.create(NAME, DESCRIPTION, LOGO_URL);

        assertAll(
                () -> assertThat(brand.getName()).isEqualTo(NAME),
                () -> assertThat(brand.getDescription()).isEqualTo(DESCRIPTION),
                () -> assertThat(brand.getLogoUrl()).isEqualTo(LOGO_URL)
        );
    }

    @Test
    @DisplayName("description 과 logoUrl 은 비어있어도 생성된다")
    void givenNullDescriptionAndLogo_whenCreate_thenBrandIsCreated() {
        Brand brand = Brand.create(NAME, null, null);

        assertAll(
                () -> assertThat(brand.getDescription()).isNull(),
                () -> assertThat(brand.getLogoUrl()).isNull()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("이름이 비어있으면 CoreException 이 발생한다")
    void givenBlankName_whenCreate_thenThrowsCoreException(String invalidName) {
        assertThatThrownBy(() -> Brand.create(invalidName, DESCRIPTION, LOGO_URL))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드 이름은 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("update 로 이름, 설명, 로고를 변경할 수 있다")
    void givenBrand_whenUpdate_thenChangesAllFields() {
        Brand brand = Brand.create(NAME, DESCRIPTION, LOGO_URL);

        brand.update("뉴루퍼스", "새로운 슬로건", "https://cdn.loopers.com/brands/new.png");

        assertAll(
                () -> assertThat(brand.getName()).isEqualTo("뉴루퍼스"),
                () -> assertThat(brand.getDescription()).isEqualTo("새로운 슬로건"),
                () -> assertThat(brand.getLogoUrl()).isEqualTo("https://cdn.loopers.com/brands/new.png")
        );
    }

    @Test
    @DisplayName("update 에서 description 과 logoUrl 을 null 로 변경할 수 있다")
    void givenBrand_whenUpdateWithNulls_thenFieldsBecomeNull() {
        Brand brand = Brand.create(NAME, DESCRIPTION, LOGO_URL);

        brand.update("뉴루퍼스", null, null);

        assertAll(
                () -> assertThat(brand.getDescription()).isNull(),
                () -> assertThat(brand.getLogoUrl()).isNull()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("update 시 이름이 비어있으면 CoreException 이 발생한다")
    void givenBrand_whenUpdateWithBlankName_thenThrowsCoreException(String invalidName) {
        Brand brand = Brand.create(NAME, DESCRIPTION, LOGO_URL);

        assertThatThrownBy(() -> brand.update(invalidName, DESCRIPTION, LOGO_URL))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("브랜드 이름은 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("delete 호출 시 deletedAt 이 채워진다")
    void givenBrand_whenDelete_thenDeletedAtIsSet() {
        Brand brand = Brand.create(NAME, DESCRIPTION, LOGO_URL);

        brand.delete();

        assertThat(brand.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete 는 멱등하다 (두 번 호출해도 동일하게 동작)")
    void givenDeletedBrand_whenDeleteAgain_thenDeletedAtRemainsUnchanged() {
        Brand brand = Brand.create(NAME, DESCRIPTION, LOGO_URL);

        brand.delete();
        var firstDeletedAt = brand.getDeletedAt();
        brand.delete();

        assertThat(brand.getDeletedAt()).isEqualTo(firstDeletedAt);
    }
}
