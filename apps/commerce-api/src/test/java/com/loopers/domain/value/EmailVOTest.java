package com.loopers.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EmailVOTest {

    @DisplayName("이메일 유효성 테스트 시나리오")
    @TestFactory
    public Collection<DynamicTest> emailDynamicTest() {
        return List.of(
                DynamicTest.dynamicTest("올바르지 않은 이메일 유형의 경우에는 Exception이 발생한다.", () -> {
                    // given
                    String invalid1 = "testemail@email";
                    String invalid2 = "testemail";
                    String invalid3 = "testemail.com";

                    // when then
                    assertThatThrownBy(() -> new EmailVO(invalid1))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("올바르지 않는 이메일 형식 입니다.");

                    assertThatThrownBy(() -> new EmailVO(invalid2))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("올바르지 않는 이메일 형식 입니다.");

                    assertThatThrownBy(() -> new EmailVO(invalid3))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("올바르지 않는 이메일 형식 입니다.");
                }),
                DynamicTest.dynamicTest("유효한 Email 형식의 경우에는 성공한다.", () -> {
                    // given
                    String valid = "test@email.com";

                    // when
                    EmailVO emailVO = new EmailVO(valid);

                    // then
                    assertThat(emailVO.email()).isEqualTo(valid);
                })
        );
    }
}
