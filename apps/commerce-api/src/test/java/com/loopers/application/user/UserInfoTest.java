package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import fixture.UserModelFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
