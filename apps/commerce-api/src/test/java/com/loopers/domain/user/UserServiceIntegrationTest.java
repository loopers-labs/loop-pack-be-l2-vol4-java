package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입")
    @Nested
    class Signup {

        // happy path 우선 작성
        @DisplayName("정상적인 인자들이 들어오면, 회원이 생성된다.")
        @Test
        void createUser_whenValidArgumentsAreProvided() {
            // arrange
            // 회원 정보 생성
            String userId = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "test@naver.com";

            // act = then
            UserModel userModel = userService.signup(userId, name, password, birthDate, email);

            // assert
            // userModel이 정확히 생성되었는지 검증
            Assertions.assertAll(
                () -> Assertions.assertNotNull(userModel.getId()),
                () -> Assertions.assertEquals(userId, userModel.getUserId()),
                () -> Assertions.assertEquals(name, userModel.getName()),
                () -> Assertions.assertEquals(birthDate, userModel.getBirthDate()),
                () -> Assertions.assertEquals(email, userModel.getEmail())
                // () -> Assertions.assertNotEquals(password, userModel.getPassword())
            );
        }
    }

    @DisplayName("회원 정보 조회")
    @Nested
    class getUser {

        // happy path 우선 작성

    }

    @DisplayName("비밀번호 수정")
    @Nested
    class changePassword {
        // happy path 작성
    }


}
