package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    private UserModel user;

    @BeforeEach
    void setUp() {
        user = new UserModel("user123", "encodedPassword!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
    }

    @DisplayName("register()를 호출할 때,")
    @Nested
    class Register {

        @DisplayName("Given 중복되지 않은 loginId / When 등록 요청 / Then 저장된 UserModel이 반환된다.")
        @Test
        void returnsRegisteredUser_whenLoginIdIsUnique() {
            // arrange
            given(userRepository.existsByLoginId(user.getLoginId())).willReturn(false);
            given(userRepository.save(user)).willReturn(user);

            // act
            UserModel result = userService.register(user);

            // assert
            assertThat(result).isEqualTo(user);
        }

        @DisplayName("Given 이미 존재하는 loginId / When 등록 요청 / Then CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdIsDuplicated() {
            // arrange
            given(userRepository.existsByLoginId(user.getLoginId())).willReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.register(user)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            then(userRepository).should(never()).save(user);
        }
    }
}
