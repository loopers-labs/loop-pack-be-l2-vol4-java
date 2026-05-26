package com.loopers.user.application;

import com.loopers.user.domain.Gender;
import com.loopers.user.domain.PasswordEncryptor;
import com.loopers.user.domain.UserModel;
import com.loopers.user.domain.UserRepository;
import com.loopers.user.domain.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserFacadeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncryptor passwordEncryptor;

    private UserService userService;
    private UserFacade userFacade;

    @BeforeEach
    void setUp() {
        // UserService는 순수 Java — 직접 생성해 실제 로직을 검증한다
        userService = new UserService(passwordEncryptor);
        userFacade = new UserFacade(userService, userRepository);
    }

    @DisplayName("내 정보 조회를 할 때,")
    @Nested
    class GetMyInfo {

        // E2E에서 존재하지 않는 ID 요청 시 LoginUserResolver가 먼저 차단하여
        // UserFacade.getMyInfo()의 NOT_FOUND 경로가 실제로 도달되지 않는 갭을 보완
        @DisplayName("존재하지 않는 loginId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            // arrange
            given(userRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userFacade.getMyInfo("nonexistent")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하는 loginId이면, 회원 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenUserExists() {
            // arrange
            UserModel user = new UserModel("user1", "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);
            given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));

            // act
            UserInfo result = userFacade.getMyInfo("user1");

            // assert
            assertThat(result).isNotNull();
            assertThat(result.loginId()).isEqualTo("user1");
        }
    }
}
