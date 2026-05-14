package com.loopers.domain.user;

import com.loopers.fixture.UserModelFixture;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class UserServiceIntegrationTest {

    @Autowired UserService userService;
    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;
    // @Transactional 로도 롤백할수 있으나 한계가 있어 사용
    // 별도 스레드에서 트랜잭션이 열리는 경우 ex: 비동기
    // TestRestTemplate 이나 MockMv에서 실제 커밋이 일어나는 경우
    // AUTO_INCREMENT(PK 시퀀스)까지 초기화하고 싶을 때

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("회원가입을 할때")
    class SignUp {
        @DisplayName("정상 정보로 가입하면, 저장되고 가입된 회원 정보를 반환한다.")
        @Test
        void given_validInput_when_signUp_then_userExists() {
            // Arrange
            UserModel inputUserModel = UserModelFixture.aUser().build();

            // Act
            UserModel resultUserModel = userService.signUp(inputUserModel);

            // Assert
            assertAll(
                    () -> assertThat(resultUserModel.getId()).isNotNull(),
                    () -> assertThat(resultUserModel.getLoginId()).isEqualTo(inputUserModel.getLoginId()),
                    () -> assertThat(resultUserModel.getName()).isEqualTo(inputUserModel.getName()),
                    () -> assertThat(resultUserModel.getBirthday()).isEqualTo(inputUserModel.getBirthday()),
                    () -> assertThat(resultUserModel.getEmail()).isEqualTo(inputUserModel.getEmail()),
                    () -> assertThat(resultUserModel.getPassword())
                            .isNotEqualTo(inputUserModel.getPassword())
                            .isNotBlank(),
                    () -> assertThat(userJpaRepository.count()).isEqualTo(1L)
            );
        }

    }




}
