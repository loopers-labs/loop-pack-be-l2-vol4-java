package com.loopers.application.user;

import com.loopers.domain.user.FakePasswordEncryptor;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserInfoTest {

    private final PasswordEncryptor passwordEncryptor = new FakePasswordEncryptor("encrypted:");

    @DisplayName("UserModel 로부터 UserInfo 를 생성할 때, ")
    @Nested
    class From {

        @DisplayName("UserModel 의 필드를 UserInfo 로 정확히 매핑한다")
        @Test
        void mapsUserModelFieldsToUserInfo() {
            // given
            String loginId = "user01";
            String password = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;
            UserModel userModel = new UserModel(loginId, password, name, birthDate, email, gender, passwordEncryptor);

            // when
            UserInfo userInfo = UserInfo.from(userModel);

            // then
            assertThat(userInfo.id()).isEqualTo(userModel.getId());
            assertThat(userInfo.loginId()).isEqualTo(loginId);
            assertThat(userInfo.name()).isEqualTo(name);
            assertThat(userInfo.birthDate()).isEqualTo(birthDate);
            assertThat(userInfo.email()).isEqualTo(email);
        }
    }
}
