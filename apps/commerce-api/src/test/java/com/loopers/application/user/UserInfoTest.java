package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.CoreException;
import fixture.UserModelFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UserInfoTest {

    @DisplayName("유저의 원본이름을 주면 끝 자리를 * 로 마스킹한다")
    @Test
    public void userInfoTest() {
        // given
        UserModelFixture defaults = UserModelFixture.defaults();
        UserModel userModel = UserModel.of(defaults.loginId(), defaults.name(), defaults.password(), new BirthVO(defaults.birth()), new EmailVO(defaults.email()));

        // when
        UserInfo userInfo = UserInfo.from(userModel);

        // then
        assertNotEquals(defaults.name(), userInfo.name());
        assertEquals("테스*", userInfo.name());
    }

    @DisplayName("유저의 원본이름이 없는 경우에는 Exception이 발생한다.")
    @Test
    public void userInfoTestThrown() {
        // given
        UserModelFixture defaults = UserModelFixture.defaults();
        UserModel userModel = UserModel.of(defaults.loginId(), null, defaults.password(), new BirthVO(defaults.birth()), new EmailVO(defaults.email()));

        // when then
        assertThatThrownBy(() -> UserInfo.from(userModel))
                .isInstanceOf(CoreException.class)
                .hasMessage("유저의 이름이 빈 값입니다.");
    }
}
