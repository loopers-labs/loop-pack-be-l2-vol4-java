package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.domain.value.PasswordVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UserInfoTest {

    @DisplayName("유저의 원본이름을 주면 끝 자리를 * 로 마스킹한다")
    @Test
    public void userInfoTest() {
        // given
        String loginId = "test";
        String name = "테스터";
        LocalDate localDate = LocalDate.of(1993, Month.MARCH, 16);
        String password = "test_1234";
        String email = "test@tester.com";

        UserModel userModel = UserModel.of(loginId, name, new BirthVO(localDate), new PasswordVO(password), new EmailVO(email));

        // when
        UserInfo userInfo = UserInfo.from(userModel);

        // then
        assertNotEquals(name, userInfo.name());
        assertEquals("테스*", userInfo.name());
    }
}
