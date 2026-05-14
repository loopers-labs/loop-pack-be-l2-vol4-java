package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserFacadeIntegrationTest {

  @Autowired private UserFacade userFacade;

  @Autowired private DatabaseCleanUp databaseCleanUp;

  @MockitoSpyBean private UserRepository userRepository;

  @AfterEach
  void tearDown() {
    databaseCleanUp.truncateAllTables();
  }

  @DisplayName("회원 가입을 할 때,")
  @Nested
  class SignUp {

    @DisplayName("필요 정보가 모두 유효하면 User 저장이 수행된다.")
    @Test
    void savesUser_whenRequiredFieldsAreValid() {
      // arrange
      SignUpCommand command =
          new SignUpCommand("loopers01", "Password1!", "홍길동", "1995-05-15", "loopers@example.com");

      // act
      UserInfo result = userFacade.signUp(command);

      // assert
      assertAll(
          () -> verify(userRepository).save(any(User.class)),
          () -> assertThat(result.id()).isNotNull(),
          () -> assertThat(result.loginId()).isEqualTo(command.loginId()),
          () -> assertThat(result.name()).isEqualTo(command.name()),
          () -> assertThat(result.birthDate()).isEqualTo(command.birthDate()),
          () -> assertThat(result.email()).isEqualTo(command.email()));
    }

    @DisplayName("이미 가입된 ID 로 회원가입 시도 시 실패한다.")
    @Test
    void throwsConflictException_whenLoginIdAlreadyExists() {
      // arrange
      SignUpCommand command =
          new SignUpCommand("loopers01", "Password1!", "홍길동", "1995-05-15", "loopers@example.com");
      userFacade.signUp(command);

      SignUpCommand duplicateCommand =
          new SignUpCommand("loopers01", "Password1!", "김철수", "1997-07-20", "other@example.com");

      // act
      CoreException exception =
          assertThrows(CoreException.class, () -> userFacade.signUp(duplicateCommand));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }
  }

  @DisplayName("내 정보 조회를 할 때,")
  @Nested
  class MyProfileTest {

    @DisplayName("해당 ID 의 회원이 존재할 경우, 회원 정보가 반환된다.")
    @Test
    void returnsUserInfo_whenUserExists() {
      // arrange
      SignUpCommand command =
          new SignUpCommand("loopers01", "Password1!", "홍길동", "1995-05-15", "loopers@example.com");
      UserInfo signedUp = userFacade.signUp(command);

      // act
      UserInfo result = userFacade.getMyInfo("loopers01");

      // assert
      assertAll(
          () -> assertThat(result).isNotNull(),
          () -> assertThat(result.id()).isEqualTo(signedUp.id()),
          () -> assertThat(result.loginId()).isEqualTo(command.loginId()),
          () -> assertThat(result.name()).isEqualTo(command.name()),
          () -> assertThat(result.birthDate()).isEqualTo(command.birthDate()),
          () -> assertThat(result.email()).isEqualTo(command.email()));
    }

    @DisplayName("해당 ID 의 회원이 존재하지 않을 경우, NOT_FOUND 예외가 발생한다.")
    @Test
    void throwsNotFound_whenUserDoesNotExist() {
      // act
      CoreException exception =
          assertThrows(CoreException.class, () -> userFacade.getMyInfo("nonexistent"));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }
  }
}
