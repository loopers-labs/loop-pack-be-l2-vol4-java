package com.loopers.application.user;

import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRole;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserFacadeTest {

    @InjectMocks
    private UserFacade userFacade;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원가입 요청 시 중복 아이디가 없고 비밀번호 검증을 통과하면 사용자가 정상 저장된다.")
    void signUp_Success() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        String name = "테스터";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        String email = "tester01@example.com";

        given(userRepository.existsByLoginId(loginId)).willReturn(false);
        given(passwordEncoder.encode(password)).willReturn("encoded_password");

        // when
        userFacade.signUp(loginId, password, name, birthDate, email);

        // then
        verify(userRepository).existsByLoginId(loginId);
        verify(passwordEncoder).encode(password);
        verify(userRepository).save(any(UserModel.class));
    }

    @Test
    @DisplayName("회원가입 요청 시 아이디가 중복되면 예외가 발생한다.")
    void signUp_DuplicateLoginId_ShouldThrowException() {
        // given
        String loginId = "tester01";
        given(userRepository.existsByLoginId(loginId)).willReturn(true);

        // when & then
        assertThrows(CoreException.class, () ->
                userFacade.signUp(loginId, "Password123!", "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com")
        );
    }

    @Test
    @DisplayName("내 정보 조회 시 로그인 자격 증명이 올바르면 masked 회원 정보를 반환한다.")
    void getMyInfo_Success() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        UserModel user = UserModel.builder()
                .loginId(loginId)
                .password("encoded_password")
                .name("홍길동")
                .role(UserRole.USER)
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("tester@example.com")
                .build();

        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(password, "encoded_password")).willReturn(true);

        // when
        UserInfo response = userFacade.getMyInfo(loginId, password);

        // then
        assertThat(response.loginId()).isEqualTo(loginId);
        assertThat(response.name()).isEqualTo("홍길*");
    }

    @Test
    @DisplayName("비밀번호 수정 요청 시 정보가 올바르면 수정된 비밀번호가 저장된다.")
    void updatePassword_Success() {
        // given
        String loginId = "tester01";
        String currentPassword = "OldPassword123!";
        String newPassword = "NewPassword123!";
        UserModel user = UserModel.builder()
                .loginId(loginId)
                .password("encoded_old_password")
                .name("테스터")
                .role(UserRole.USER)
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("tester@example.com")
                .build();

        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(currentPassword, "encoded_old_password")).willReturn(true);
        given(passwordEncoder.encode(newPassword)).willReturn("encoded_new_password");

        // when
        userFacade.updatePassword(loginId, currentPassword, currentPassword, newPassword);

        // then
        verify(userRepository).save(user);
        assertThat(user.getPassword()).isEqualTo("encoded_new_password");
    }
}
