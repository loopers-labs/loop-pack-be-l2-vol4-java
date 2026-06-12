package com.loopers.tddstudy.domain.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BrandTest {

    @Test
    @DisplayName("브랜드 생성 시 기본 상태는 DRAFT 이다")
    void 브랜드_생성_성공() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        assertThat(brand.getName()).isEqualTo("나이키");
        assertThat(brand.getDescription()).isEqualTo("스포츠 브랜드");
        assertThat(brand.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("브랜드 이름이 null 이면 예외가 발생한다")
    void 브랜드_이름_null_예외() {
        assertThatThrownBy(() -> new Brand(null, "설명", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("브랜드 이름은 필수입니다.");
    }

    @Test
    @DisplayName("브랜드 이름이 공백이면 예외가 발생한다")
    void 브랜드_이름_공백_예외() {
        assertThatThrownBy(() -> new Brand("   ", "설명", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("브랜드 이름은 필수입니다.");
    }

    @Test
    @DisplayName("브랜드를 공개하면 상태가 ACTIVE 가 된다")
    void 브랜드_공개() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        brand.publish();

        assertThat(brand.getStatus()).isEqualTo("ACTIVE");
        assertThat(brand.isActive()).isTrue();
    }

    @Test
    @DisplayName("브랜드를 소프트 삭제하면 상태가 DELETED 가 된다")
    void 브랜드_소프트_삭제() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        brand.softDelete();

        assertThat(brand.getStatus()).isEqualTo("DELETED");
        assertThat(brand.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("브랜드 이름과 설명을 수정할 수 있다")
    void 브랜드_수정_성공() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        brand.update("아디다스", "독일 스포츠 브랜드");

        assertThat(brand.getName()).isEqualTo("아디다스");
        assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드");
    }

    @Test
    @DisplayName("수정 시 이름이 공백이면 예외가 발생한다")
    void 브랜드_수정_이름_공백_예외() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        assertThatThrownBy(() -> brand.update("  ", "설명"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("브랜드 이름은 필수입니다.");
    }

    @Test
    @DisplayName("브랜드 생성 시 소유자 ID 를 설정할 수 있다")
    void 브랜드_생성_시_소유자_설정() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        assertThat(brand.getOwnerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("OPERATOR 는 DRAFT 브랜드를 볼 수 있다")
    void OPERATOR_는_DRAFT_브랜드_열람_가능() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        assertThat(brand.isVisibleTo(99L, "OPERATOR")).isTrue();
    }

    @Test
    @DisplayName("OPERATOR 는 DELETED 브랜드를 볼 수 있다")
    void OPERATOR_는_DELETED_브랜드_열람_가능() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);
        brand.softDelete();

        assertThat(brand.isVisibleTo(99L, "OPERATOR")).isTrue();
    }

    @Test
    @DisplayName("브랜드 소유자는 자신의 DRAFT 브랜드를 볼 수 있다")
    void 소유자는_자신의_DRAFT_브랜드_열람_가능() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        assertThat(brand.isVisibleTo(1L, "USER")).isTrue();
    }

    @Test
    @DisplayName("일반 유저는 타인의 DRAFT 브랜드를 볼 수 없다")
    void 일반유저는_타인의_DRAFT_브랜드_열람_불가() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);

        assertThat(brand.isVisibleTo(2L, "USER")).isFalse();
    }

    @Test
    @DisplayName("일반 유저는 ACTIVE 브랜드를 볼 수 있다")
    void 일반유저는_ACTIVE_브랜드_열람_가능() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);
        brand.publish();

        assertThat(brand.isVisibleTo(2L, "USER")).isTrue();
    }

    @Test
    @DisplayName("DELETED 브랜드는 일반 유저는 볼 수 없다")
    void DELETED_브랜드는_일반유저_열람_불가() {
        Brand brand = new Brand("나이키", "스포츠 브랜드", 1L);
        brand.softDelete();

        assertThat(brand.isVisibleTo(1L, "USER")).isFalse();
    }

}
