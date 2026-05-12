package com.loopers.domain.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class UserModelTest {
    @DisplayName("유저 모델을 생성하는 테스트")
    @Nested
    class SignUp {

        @DisplayName("정상적인 요청이 온 경우, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenRequestIsValid() {
            // arrange = given
            String id = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            String birthDate = "19950610";
            String email = "test@naver.com";

            // act = when
            UserModel userModel = new UserModel(id, password, name, birthDate, email);

            // assert = then
            Assertions.assertAll(
                () -> Assertions.assertEquals(id, userModel.getUserId()),
                () -> Assertions.assertEquals(name, userModel.getName()),
                () -> Assertions.assertEquals(birthDate, userModel.getBirthDate()),
                () -> Assertions.assertEquals(email, userModel.getEmail())
            );
        }
    }
}
