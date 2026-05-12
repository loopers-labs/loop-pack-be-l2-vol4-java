package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_LOGIN_ID = "chanhee";
    private static final String VALID_RAW_PASSWORD = "chan1234!";
    private static final String ENCODED_PASSWORD = "H@shed12";
    private static final String VALID_NAME = "김찬희";
    private static final String VALID_BIRTH_DATE = "1995-05-10";
    private static final String VALID_EMAIL = "chan950510@gmail.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입 처리 시")
    @Nested
    class SignUp {

        @DisplayName("중복되지 않은 아이디로 요청하면 암호화 후 가입완료")
        @Test
        void savesUserOnce_whenRequestIsValid() {
            // arrange
            given(userRepository.existsByLoginId(VALID_LOGIN_ID)).willReturn(false);
            given(passwordEncoder.encode(VALID_RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(UserModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            userService.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            ArgumentCaptor<UserModel> savedCaptor = ArgumentCaptor.forClass(UserModel.class);
            verify(userRepository, times(1)).existsByLoginId(VALID_LOGIN_ID);
            verify(passwordEncoder, times(1)).encode(VALID_RAW_PASSWORD);
            verify(userRepository, times(1)).save(savedCaptor.capture());

            UserModel saved = savedCaptor.getValue();
            assertAll(
                    () -> assertThat(saved.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                    () -> assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD),
                    () -> assertThat(saved.getName()).isEqualTo(VALID_NAME),
                    () -> assertThat(saved.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                    () -> assertThat(saved.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("이미 존재하는 로그인ID면 CONFLICT 예외 발생")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            given(userRepository.existsByLoginId(VALID_LOGIN_ID)).willReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // assert
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                    () -> verify(userRepository, times(1)).existsByLoginId(VALID_LOGIN_ID),
                    () -> verify(passwordEncoder, never()).encode(anyString()),
                    () -> verify(userRepository, never()).save(any(UserModel.class))
            );
        }
    }
}
