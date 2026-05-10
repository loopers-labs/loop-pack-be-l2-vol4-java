package com.loopers.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PasswordVOTest {

    @DisplayName("비밀번호 테스트 시나리오")
    @TestFactory
    Collection<DynamicTest> passwordDynamicTest() {
        return List.of(
                DynamicTest.dynamicTest("8자 미만 비밀번호는 실패한다.", () -> {
                    // given
                    String password ="1234567";

                    // when then
                    assertThatThrownBy(() -> new PasswordVO(password))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다");
                }),
                DynamicTest.dynamicTest("16자 초과 비밀번호는 실패한다.", () -> {
                    // given
                    String password ="01234567891234567";

                    // when then
                    assertThatThrownBy(() -> new PasswordVO(password))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다");
                }),
                DynamicTest.dynamicTest("대소문자, 숫자, 특수문자 이외의 다른 글자는 실패한다.", () -> {
                    // given
                    String password = "THIS_IS_비밀번호";

                    // when then
                    assertThatThrownBy(() -> new PasswordVO(password))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다");
                }),
                DynamicTest.dynamicTest("유효한 비밀번호는 성공한다.", () -> {
                    // given
                    String password = "THIS_IS_pwd";

                    // when
                    PasswordVO valid = new PasswordVO(password);

                    // then
                    assertThat(valid.password()).isEqualTo(password);
                })
        );
    }
}
