package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.member.Password;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.*;

class MemberFacadeTest {

    private MemberService memberService;
    private MemberFacade memberFacade;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        memberFacade = new MemberFacade(memberService);
    }

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 정보가 주어지면, MemberService.register()가 호출된다.")
        @Test
        void callsMemberServiceRegister_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "testUser1";
            String password = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "test@example.com";

            // Act
            memberFacade.register(loginId, password, name, birthDate, email);

            // Assert
            verify(memberService, times(1)).register(loginId, password, name, birthDate, email);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 정보가 주어지면, getMe()와 changePassword()가 순서대로 호출된다.")
        @Test
        void callsGetMeAndChangePassword_whenCredentialsAreValid() {
            // Arrange
            String loginId = "testUser1";
            String loginPw = "Password1!";
            String oldPassword = "Password1!";
            String newPassword = "NewPassword2@";

            // Act
            memberFacade.changePassword(loginId, loginPw, oldPassword, newPassword);

            // Assert
            verify(memberService, times(1)).getMe(loginId, loginPw);
            verify(memberService, times(1)).changePassword(loginId, oldPassword, newPassword);
        }
    }

    @DisplayName("내 정보를 조회할 때, ")
    @Nested
    class GetMe {

        @DisplayName("loginId와 비밀번호가 주어지면, MemberInfo를 반환한다.")
        @Test
        void returnsMemberInfo_whenLoginIdIsGiven() {
            // Arrange
            String loginId = "testUser1";
            String rawPassword = "Password1!";
            Password password = Password.of(rawPassword, "1990-01-01", new BCryptPasswordEncoder().encode(rawPassword));
            Member member = new Member(loginId, password, "홍길동", "1990-01-01", "test@example.com");
            when(memberService.getMe(loginId, rawPassword)).thenReturn(member);

            // Act
            MemberInfo result = memberFacade.getMe(loginId, rawPassword);

            // Assert
            assertAll(
                () -> assertThat(result.loginId()).isEqualTo("testUser1"),
                () -> assertThat(result.name()).isEqualTo("홍길동"),
                () -> assertThat(result.birthDate()).isEqualTo("1990-01-01"),
                () -> assertThat(result.email()).isEqualTo("test@example.com")
            );
        }
    }
}
