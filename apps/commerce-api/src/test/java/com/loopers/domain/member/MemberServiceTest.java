package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemberServiceTest {

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = spy(new BCryptPasswordEncoder());
        memberService = new MemberService(memberRepository, passwordEncoder);
    }

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 정보가 주어지면, save()가 호출된다.")
        @Test
        void callsSave_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "testUser1";
            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.empty());
            when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            memberService.register(loginId, "Password1!", "홍길동", "1990-01-01", "test@example.com");

            // Assert
            verify(memberRepository, times(1)).save(any(Member.class));
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // Arrange
            String loginId = "testUser1";
            Password password = Password.of("Password1!", "1990-01-01", new BCryptPasswordEncoder().encode("Password1!"));
            Member existingMember = new Member(loginId, password, "홍길동", "1990-01-01", "test@example.com");
            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existingMember));

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.register(loginId, "Password2@", "김철수", "1995-05-05", "other@example.com")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(memberRepository, never()).save(any(Member.class));
        }
    }

    @DisplayName("내 정보를 조회할 때, ")
    @Nested
    class GetMe {

        @DisplayName("올바른 loginId와 비밀번호가 주어지면, 회원 정보를 반환한다.")
        @Test
        void returnsMember_whenCredentialsAreValid() {
            // Arrange
            String loginId = "testUser1";
            String rawPassword = "Password1!";
            Password password = Password.of(rawPassword, "1990-01-01", new BCryptPasswordEncoder().encode(rawPassword));
            Member member = new Member(loginId, password, "홍길동", "1990-01-01", "test@example.com");
            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));

            // Act
            Member result = memberService.getMe(loginId, rawPassword);

            // Assert
            assertThat(result.getLoginId()).isEqualTo(loginId);
        }

        @DisplayName("존재하지 않는 loginId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // Arrange
            when(memberRepository.findByLoginId("notExist")).thenReturn(Optional.empty());

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.getMe("notExist", "Password1!")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // Arrange
            String loginId = "testUser1";
            Password password = Password.of("Password1!", "1990-01-01", new BCryptPasswordEncoder().encode("Password1!"));
            Member member = new Member(loginId, password, "홍길동", "1990-01-01", "test@example.com");
            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.getMe(loginId, "WrongPassword1!")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 정보가 주어지면, 새 비밀번호로 encode()가 호출된다.")
        @Test
        void callsEncode_whenCredentialsAreValid() {
            // Arrange
            String loginId = "testUser1";
            Password password = Password.of("Password1!", "1990-01-01", new BCryptPasswordEncoder().encode("Password1!"));
            Member member = new Member(loginId, password, "홍길동", "1990-01-01", "test@example.com");
            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));

            // Act
            memberService.changePassword(loginId, "Password1!", "NewPassword2@");

            // Assert
            verify(passwordEncoder, times(1)).encode("NewPassword2@");
        }

        @DisplayName("존재하지 않는 loginId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // Arrange
            when(memberRepository.findByLoginId("notExist")).thenReturn(Optional.empty());

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.changePassword("notExist", "Password1!", "NewPassword2@")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
