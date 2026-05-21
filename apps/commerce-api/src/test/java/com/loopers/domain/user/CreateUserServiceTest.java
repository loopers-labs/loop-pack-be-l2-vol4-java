package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateUserServiceTest {

    private CreateUserService createUserService;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);

        createUserService = new CreateUserService(userRepository, new FakePasswordEncryptor("encrypted:"));
    }

    @DisplayName("회원 가입을 할 때, ")
    @Nested
    class SignUp {
        @DisplayName("가입된 적 없는 ID 이면 정상적으로 수행된다.")
        @Test
        void signUp() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;

            when(userRepository.existsByLoginId(loginId)).thenReturn(false);

            // when
            createUserService.signUp(loginId, loginPw, name, birthDate, email, gender);

            // then
            verify(userRepository).save(any(UserModel.class));
        }

        @DisplayName("이미 가입된 ID 로 회원 가입 시도 시 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflictException_whenDuplicateLoginIdIsProvided() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;

            when(userRepository.existsByLoginId(loginId)).thenReturn(true);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    createUserService.signUp(loginId, loginPw, name, birthDate, email, gender)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
