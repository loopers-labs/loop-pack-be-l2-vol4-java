package com.loopers.domain.user;

import com.loopers.fixture.UserModelFixture;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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
                            .isNotEqualTo("testPw1234")
                            .isNotBlank(),
                    () -> assertThat(userJpaRepository.count()).isEqualTo(1L)
            );
        }

        @DisplayName("이미 존재하는 loginId로 가입하면, Conflict 예외가 발생한다")
        @Test
        void given_duplicateLoginId_when_signUp_then_throwsConflictException(){
            // Arrange
            UserModel firstUserModel = UserModelFixture.aUser()
                    .withLoginId("dupid")
                    .build();
            userService.signUp(firstUserModel);

            UserModel secondUserModel = UserModelFixture.aUser()
                    .withLoginId("dupid")
                    .withEmail("other@example.com") // loginId 외 다른 필드는 충돌 안 함
                    .build();

            // Act
            Throwable thrown = catchThrowable(() -> userService.signUp(secondUserModel));

            // Assert
            assertThat(thrown)
                    .isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType())
                    .isEqualTo(ErrorType.CONFLICT);
        }
    }

    @Nested
    @DisplayName("본인 정보를 조회할 때")
    class  GetMyInfo {
        @DisplayName("존재하는 userId로 조회하면, 회원 모델을 반환한다.")
        @Test
        void given_existingUserId_when_getMyInfo_then_returnsUser() {
            // Arrange
            UserModel savedUserModel = userService.signUp(UserModelFixture.aUser().build());

            // Act
            UserModel resultUserModel = userService.getMyInfo(savedUserModel.getId());

            // Assert
            assertAll(
                    () -> assertThat(resultUserModel.getId()).isEqualTo(savedUserModel.getId()),
                    () -> assertThat(resultUserModel.getLoginId()).isEqualTo(savedUserModel.getLoginId()),
                    () -> assertThat(resultUserModel.getName()).isEqualTo("테스터"),
                    () -> assertThat(resultUserModel.getBirthday()).isEqualTo(savedUserModel.getBirthday()),
                    () -> assertThat(resultUserModel.getEmail()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 userId로 조회하면, NotFound 예외가 발생한다.")
        @Test
        void given_nonExistingUserId_when_getMyInfo_then_throwsNotFoundException() {
            // Arrange
            Long unknownUserId = 9999L;

            // Act
            Throwable thrown = catchThrowable(() -> userService.getMyInfo(unknownUserId));

            // Assert
            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("비밀번호를 수정할 때")
    class ChangePassword {
        @DisplayName("현재 비밀번호가 맞고 새 비밀번호가 유효하면, 비밀번호가 변경된다.")
        @Test
        void given_correctCurrentAndValidNewPassword_when_changePassword_then_passwordChanges() {
            // Arrange
            UserModel savedUserModel = userService.signUp(UserModelFixture.aUser().build());
            String currentPassword = "testPw1234";   // fixture default raw
            String newPassword = "newPw5678";
            String oldHash = savedUserModel.getPassword();

            // Act
            userService.changePassword(savedUserModel.getId(), currentPassword, newPassword);

            // Assert
            UserModel updatedUserModel = userService.getMyInfo(savedUserModel.getId());
            assertAll(
                    () -> assertThat(updatedUserModel.getPassword()).isNotEqualTo(oldHash),       // 기존 hash와 다름
                    () -> assertThat(updatedUserModel.getPassword()).isNotEqualTo(newPassword)    // 평문 저장 X (해싱됨)
            );
        }

        @DisplayName("현재 비밀번호가 틀리면, Unauthorized 예외가 발생한다.")
        @Test
        void given_wrongCurrentPassword_when_changePassword_then_throwsUnauthorizedException() {
            // Arrange
            UserModel savedUserModel = userService.signUp(UserModelFixture.aUser().build());

            // Act
            Throwable thrown = catchThrowable(() ->
                    userService.changePassword(savedUserModel.getId(), "wrongPw9999", "newPw5678"));

            // Assert
            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BadRequest 예외가 발생한다.")
        @Test
        void given_newPasswordSameAsCurrent_when_changePassword_then_throwsBadRequestException() {
            // Arrange
            UserModel savedUserModel = userService.signUp(UserModelFixture.aUser().build());
            String currentPassword = "testPw1234";

            // Act
            Throwable thrown = catchThrowable(() ->
                    userService.changePassword(savedUserModel.getId(), currentPassword, currentPassword));

            // Assert
            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 userId면, NotFound 예외가 발생한다.")
        @Test
        void given_nonExistingUserId_when_changePassword_then_throwsNotFoundException() {
            // Act
            Throwable thrown = catchThrowable(() ->
                    userService.changePassword(9999L, "anyPw1234", "newPw5678"));

            // Assert
            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

    }

    @Nested
    @DisplayName("인증할 때")
    class Authenticate {

        @DisplayName("올바른 loginId와 비밀번호로 인증하면, 회원 모델을 반환한다.")
        @Test
        void given_correctCredentials_when_authenticate_then_returnsUser() {
            // Arrange
            UserModel savedUserModel = userService.signUp(UserModelFixture.aUser().build());

            // Act
            UserModel resultUserModel = userService.authenticate("testid", "testPw1234");

            // Assert
            assertThat(resultUserModel.getId()).isEqualTo(savedUserModel.getId());
        }

        @DisplayName("비밀번호가 틀리면, Unauthorized 예외가 발생한다.")
        @Test
        void given_wrongPassword_when_authenticate_then_throwsUnauthorizedException() {
            // Arrange
            userService.signUp(UserModelFixture.aUser().build());

            // Act
            Throwable thrown = catchThrowable(() ->
                    userService.authenticate("testid", "wrongPw9999"));

            // Assert
            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType())
                    .isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 loginId로 인증하면, Unauthorized 예외가 발생한다.")
        @Test
        void given_nonExistingLoginId_when_authenticate_then_throwsUnauthorizedException() {
            // Act
            Throwable thrown = catchThrowable(() ->
                    userService.authenticate("notexist", "anyPw1234"));

            // Assert
            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType())
                    .isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
