package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.loopers.fixture.BrandModelFixture.aBrand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BrandModelTest {

    @Nested
    @DisplayName("BrandModel 생성")
    class Create {

        @DisplayName("유효한 값으로 생성하면 활성 상태의 Brand가 만들어진다")
        @Test
        void given_validInput_when_create_then_createsActiveBrand() {
            BrandModel brand = new BrandModel("나이키", "글로벌 스포츠 브랜드");

            assertAll(
                    () -> assertThat(brand.getName()).isEqualTo("나이키"),
                    () -> assertThat(brand.getDescription()).isEqualTo("글로벌 스포츠 브랜드"),
                    () -> assertThat(brand.isActive()).isTrue()
            );
        }

        @DisplayName("이름이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        void given_nullOrBlankName_when_create_then_throwsBadRequest(String invalidName) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> aBrand().withName(invalidName).build()
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 100자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nameOverMaxLength_when_create_then_throwsBadRequest() {
            String tooLong = "가".repeat(101);

            CoreException result = assertThrows(
                    CoreException.class,
                    () -> aBrand().withName(tooLong).build()
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 100자이면 정상 생성된다 (경계)")
        @Test
        void given_nameAtMaxLength_when_create_then_creates() {
            String name = "가".repeat(100);

            BrandModel brand = aBrand().withName(name).build();

            assertThat(brand.getName()).isEqualTo(name);
        }

        @DisplayName("설명은 null이어도 정상 생성된다 (nullable)")
        @Test
        void given_nullDescription_when_create_then_creates() {
            BrandModel brand = aBrand().withDescription(null).build();

            assertAll(
                    () -> assertThat(brand.getDescription()).isNull(),
                    () -> assertThat(brand.isActive()).isTrue()
            );
        }

        @DisplayName("설명이 1000자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_descriptionOverMaxLength_when_create_then_throwsBadRequest() {
            String tooLong = "가".repeat(1001);

            CoreException result = assertThrows(
                    CoreException.class,
                    () -> aBrand().withDescription(tooLong).build()
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @DisplayName("delete() 하면 비활성(isActive=false)이 된다")
        @Test
        void given_activeBrand_when_delete_then_inactive() {
            BrandModel brand = aBrand().build();

            brand.delete();

            assertThat(brand.isActive()).isFalse();
        }

        @DisplayName("삭제된 Brand를 restore() 하면 다시 활성이 된다")
        @Test
        void given_deletedBrand_when_restore_then_active() {
            BrandModel brand = aBrand().build();
            brand.delete();

            brand.restore();

            assertThat(brand.isActive()).isTrue();
        }
    }
}
