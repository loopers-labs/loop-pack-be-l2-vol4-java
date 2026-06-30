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
    private static final String THUMBNAIL = "https://cdn.loopers.com/products/shirt.png";

    @Test
    @DisplayName("create 로 생성하면 모든 필드와 함께 status=ON_SALE 으로 초기화된다")
    void givenValidFields_whenCreate_thenStoresAllFieldsWithDefaults() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);

        assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(BRAND_ID),
                () -> assertThat(product.getName()).isEqualTo(NAME),
                () -> assertThat(product.getDescription()).isEqualTo(DESCRIPTION),
                () -> assertThat(product.getPrice().value()).isEqualTo(PRICE),
                () -> assertThat(product.getThumbnailUrl()).isEqualTo(THUMBNAIL),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE)
        );
    }

    @Test
    @DisplayName("create 직후 likeCount 는 0 으로 초기화된다")
    void givenNewProduct_whenCreate_thenLikeCountIsZero() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);

        assertThat(product.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("description 과 thumbnailUrl 은 null 이어도 생성된다")
    void givenNullOptionalFields_whenCreate_thenStoresNull() {
        Product product = Product.create(BRAND_ID, NAME, null, PRICE, null);

        assertAll(
                () -> assertThat(product.getDescription()).isNull(),
                () -> assertThat(product.getThumbnailUrl()).isNull()
        );
    }

    @Test
    @DisplayName("price 가 0 이어도 생성된다 (사은품/이벤트 허용)")
    void givenZeroPrice_whenCreate_thenStoresZero() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, 0L, THUMBNAIL);

        assertThat(product.getPrice().value()).isZero();
    }

    @Test
    @DisplayName("brandId 가 null 이면 CoreException 이 발생한다")
    void givenNullBrandId_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Product.create(null, NAME, DESCRIPTION, PRICE, THUMBNAIL))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("brandId 는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("이름이 비어있으면 CoreException 이 발생한다")
    void givenBlankName_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> Product.create(BRAND_ID, invalid, DESCRIPTION, PRICE, THUMBNAIL))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품 이름은 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -100L})
    @DisplayName("price 가 음수이면 CoreException 이 발생한다")
    void givenNegativePrice_whenCreate_thenThrowsCoreException(long invalid) {
        assertThatThrownBy(() -> Product.create(BRAND_ID, NAME, DESCRIPTION, invalid, THUMBNAIL))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("금액은 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("update 로 이름, 설명, 가격, 썸네일을 변경할 수 있다")
    void givenProduct_whenUpdate_thenChangesFields() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);

        product.update("프린트 셔츠", "프린트 패턴 셔츠", 35_000L, "https://cdn.loopers.com/products/print.png");

        assertAll(
                () -> assertThat(product.getName()).isEqualTo("프린트 셔츠"),
                () -> assertThat(product.getDescription()).isEqualTo("프린트 패턴 셔츠"),
                () -> assertThat(product.getPrice().value()).isEqualTo(35_000L),
                () -> assertThat(product.getThumbnailUrl()).isEqualTo("https://cdn.loopers.com/products/print.png")
        );
    }

    @Test
    @DisplayName("update 후에도 brandId 는 변경되지 않는다 (수정 대상 아님)")
    void givenProduct_whenUpdate_thenBrandIdIsUnchanged() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);

        product.update("프린트 셔츠", "설명", 35_000L, THUMBNAIL);

        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("update 시 이름이 비어있으면 CoreException 이 발생한다")
    void givenProduct_whenUpdateWithBlankName_thenThrowsCoreException(String invalid) {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);

        assertThatThrownBy(() -> product.update(invalid, DESCRIPTION, PRICE, THUMBNAIL))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("상품 이름은 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("suspend 로 판매중지(SUSPENDED) 상태가 된다")
    void givenOnSaleProduct_whenSuspend_thenStatusBecomesSuspended() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);

        product.suspend();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SUSPENDED);
    }

    @Test
    @DisplayName("resume 로 다시 판매중(ON_SALE) 상태가 된다")
    void givenSuspendedProduct_whenResume_thenStatusBecomesOnSale() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);
        product.suspend();

        product.resume();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("delete 호출 시 deletedAt 이 채워진다")
    void givenProduct_whenDelete_thenDeletedAtIsSet() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);

        product.delete();

        assertThat(product.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete 는 멱등하다")
    void givenDeletedProduct_whenDeleteAgain_thenDeletedAtRemainsUnchanged() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, THUMBNAIL);

        product.delete();
        var firstDeletedAt = product.getDeletedAt();
        product.delete();

        assertThat(product.getDeletedAt()).isEqualTo(firstDeletedAt);
    }
}
