package com.loopers.tddstudy.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

    @Test
    @DisplayName("상품 생성 시 기본 상태는 DRAFT 이다")
    void create_product_default_status_is_draft() {
        Product product = new Product("나이키 운동화", 50000, 10, 1L);

        assertThat(product.getName()).isEqualTo("나이키 운동화");
        assertThat(product.getPrice()).isEqualTo(50000);
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(product.getBrandId()).isEqualTo(1L);
        assertThat(product.getStatus()).isEqualTo("DRAFT");
        assertThat(product.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("상품 이름이 null 이면 예외가 발생한다")
    void create_product_null_name_throws_exception() {
        assertThatThrownBy(() -> new Product(null, 50000, 10, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 이름은 필수입니다.");
    }

    @Test
    @DisplayName("상품 이름이 공백이면 예외가 발생한다")
    void create_product_blank_name_throws_exception() {
        assertThatThrownBy(() -> new Product("  ", 50000, 10, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 이름은 필수입니다.");
    }

    @Test
    @DisplayName("가격이 0 이하면 예외가 발생한다")
    void create_product_zero_price_throws_exception() {
        assertThatThrownBy(() -> new Product("나이키 운동화", 0, 10, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가격은 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("재고가 0 미만이면 예외가 발생한다")
    void create_product_negative_stock_throws_exception() {
        assertThatThrownBy(() -> new Product("나이키 운동화", 50000, -1, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재고는 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("재고가 충분하면 true 를 반환한다")
    void has_enough_stock_returns_true() {
        Product product = new Product("나이키 운동화", 50000, 10, 1L);

        assertThat(product.hasEnoughStock(10)).isTrue();
    }

    @Test
    @DisplayName("재고가 부족하면 false 를 반환한다")
    void has_enough_stock_returns_false() {
        Product product = new Product("나이키 운동화", 50000, 10, 1L);

        assertThat(product.hasEnoughStock(11)).isFalse();
    }

    @Test
    @DisplayName("재고를 차감할 수 있다")
    void decrease_stock_success() {
        Product product = new Product("나이키 운동화", 50000, 10, 1L);

        product.decreaseStock(3);

        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 부족 시 차감하면 예외가 발생한다")
    void decrease_stock_insufficient_throws_exception() {
        Product product = new Product("나이키 운동화", 50000, 5, 1L);

        assertThatThrownBy(() -> product.decreaseStock(6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재고가 부족합니다.");
    }

    @Test
    @DisplayName("재고를 복구할 수 있다")
    void restore_stock_success() {
        Product product = new Product("나이키 운동화", 50000, 5, 1L);
        product.decreaseStock(3);

        product.restoreStock(3);

        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("좋아요 수를 증가시킬 수 있다")
    void increase_like_count_success() {
        Product product = new Product("나이키 운동화", 50000, 10, 1L);

        product.increaseLikeCount();

        assertThat(product.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수는 0 미만으로 내려가지 않는다")
    void decrease_like_count_does_not_go_below_zero() {
        Product product = new Product("나이키 운동화", 50000, 10, 1L);

        product.decreaseLikeCount();

        assertThat(product.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("상품을 공개하면 상태가 ACTIVE 가 된다")
    void publish_product_status_becomes_active() {
        Product product = new Product("나이키 운동화", 50000, 10, 1L);

        product.publish();

        assertThat(product.getStatus()).isEqualTo("ACTIVE");
        assertThat(product.isActive()).isTrue();
    }

    @Test
    @DisplayName("상품을 소프트 삭제하면 상태가 DELETED 가 된다")
    void soft_delete_product_status_becomes_deleted() {
        Product product = new Product("나이키 운동화", 50000, 10, 1L);

        product.softDelete();

        assertThat(product.getStatus()).isEqualTo("DELETED");
        assertThat(product.isDeleted()).isTrue();
    }
}
