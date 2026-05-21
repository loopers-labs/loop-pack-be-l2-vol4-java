package com.loopers.domain.user;

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

import javax.print.attribute.standard.MediaSize;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserServiceIntegrationTest {
   private final String LOGIN_ID = "testId";
   private final String PASSWORD = "validPassword123";
   private final String NAME = "임찬빈";
   private final String BIRTHDATE = "1998-04-11";
   private final String EMAIL = "test@test.com";

    @Autowired
    private UserService userService;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입 시,")
    @Nested
    class SignUp {

        @DisplayName("정상 정보로 회원 가입 시, User 저장이 수행된다.")
        @Test
        void savesUser_whenValidInfoProvided() {
            // arrange


            // act
            UserModel result = userService.signUp(LOGIN_ID, PASSWORD, NAME, BIRTHDATE, EMAIL);

            // assert
            assertAll(
                () -> verify(userRepository, times(1)).save(any(UserModel.class)),
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(LOGIN_ID),
                () -> assertThat(result.getName()).isEqualTo(NAME),
                () -> assertThat(result.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(result.getPassword()).isNotEqualTo(PASSWORD)   // 암호화되어 평문과 달라야 함
            );
        }

        @DisplayName("이미 가입된 ID로 회원 가입 시도 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            String duplicatedLoginId = "testId";
            userService.signUp(duplicatedLoginId, "validPassword123", "임찬빈", "1998-04-11", "test@test.com");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.signUp(duplicatedLoginId, "anotherPassword456", "다른사람", "2000-01-01", "other@test.com"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
    @DisplayName(" 내 정보 조회 시,")
    @Nested
    class GetMyInfo{

        @DisplayName("존재하는 loginId로 조회하면, 회원 정보가 반환된다.")
        @Test
        void returnUser_whenLoginIdExists(){
            // arrange
            UserModel userModel = userService.signUp(LOGIN_ID,PASSWORD,NAME,BIRTHDATE,EMAIL);
            // act
            UserModel result = userService.getMyInfo(LOGIN_ID);
            // assert
            assertAll(
                    () -> assertThat(result).isNotNull(),
                    () -> assertThat(result.getLoginId()).isEqualTo(LOGIN_ID),
                    () -> assertThat(result.getName()).isEqualTo(NAME),
                    () -> assertThat(result.getBirthDate()).isEqualTo(LocalDate.parse(BIRTHDATE)),
                    () -> assertThat(result.getEmail()).isEqualTo(EMAIL)
            );
        }

        @DisplayName("존재하지 않는 loginId 로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            String notExistLoginId = "notExist123";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.getMyInfo(notExistLoginId));
            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
