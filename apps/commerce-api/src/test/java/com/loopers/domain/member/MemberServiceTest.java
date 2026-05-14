package com.loopers.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("로그인 ID가 5자 미만이면 예외가 발생한다.")
    void signUp_ShortLoginId_ShouldThrowException() {
        // given
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                "id", "Password123!", "테스터", LocalDate.of(1990, 1, 1), "tester@example.com"
        );

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.signUp(command));
    }

    @Test
    @DisplayName("로그인 ID가 영문/숫자가 아니면 예외가 발생한다.")
    void signUp_InvalidLoginIdFormat_ShouldThrowException() {
        // given
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                "tester!!", "Password123!", "테스터", LocalDate.of(1990, 1, 1), "tester@example.com"
        );

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.signUp(command));
    }

    @Test
    @DisplayName("회원가입 시 비밀번호가 암호화되어 저장된다.")
    void signUp_ShouldEncryptPassword() {
        // given
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                "tester01", "Password123!", "테스터", LocalDate.of(1990, 1, 1), "tester@example.com"
        );
        given(memberRepository.existsByLoginId(any())).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("encryptedPassword");

        // when
        memberService.signUp(command);

        // then
        verify(memberRepository).save(argThat(member -> 
                member.getPassword().equals("encryptedPassword")
        ));
    }

    @Test
    @DisplayName("중복된 loginId로 가입 시 예외가 발생한다.")
    void signUp_DuplicateLoginId_ShouldThrowException() {
        // given
        String duplicateId = "tester01";
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                duplicateId, "Password123!", "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com"
        );
        given(memberRepository.existsByLoginId(duplicateId)).willReturn(true);

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.signUp(command));
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 예외가 발생한다.")
    void signUp_ShortPassword_ShouldThrowException() {
        // given
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                "tester01", "Pass1!", "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com"
        );

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.signUp(command));
    }

    @Test
    @DisplayName("이름 형식이 올바르지 않으면 예외가 발생한다.")
    void signUp_InvalidName_ShouldThrowException() {
        // given
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                "tester01", "Password123!", "홍길동1", LocalDate.of(1990, 1, 1), "tester@example.com"
        );

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.signUp(command));
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 예외가 발생한다.")
    void signUp_InvalidEmail_ShouldThrowException() {
        // given
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                "tester01", "Password123!", "홍길동", LocalDate.of(1990, 1, 1), "invalid-email"
        );

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.signUp(command));
    }

    @Test
    @DisplayName("생년월일이 미래 날짜이면 예외가 발생한다.")
    void signUp_FutureBirthDate_ShouldThrowException() {
        // given
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                "tester01", "Password123!", "홍길동", LocalDate.now().plusDays(1), "tester@example.com"
        );

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.signUp(command));
    }

    @Test
    @DisplayName("회원 조회 시 로그인 ID 형식이 올바르지 않으면 예외가 발생한다.")
    void getMember_InvalidLoginId_ShouldThrowException() {
        // when & then
        assertThrows(RuntimeException.class, () -> memberService.getMember("id", "password"));
    }

    @Test
    @DisplayName("회원 조회 시 아이디와 비밀번호가 일치하면 회원 정보를 반환한다.")
    void getMember_CorrectCredentials_ShouldReturnMemberInfo() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        String encodedPassword = "encodedPassword";
        Member member = Member.builder()
                .loginId(loginId)
                .password(encodedPassword)
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("tester01@example.com")
                .build();
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);

        // when
        MemberInfo result = memberService.getMember(loginId, password);

        // then
        assertThat(result.loginId()).isEqualTo(loginId);
        assertThat(result.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("회원 조회 시 아이디가 존재하지 않으면 예외가 발생한다.")
    void getMember_MemberNotFound_ShouldThrowException() {
        // given
        String loginId = "nonexistent";
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.empty());

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.getMember(loginId, "password"));
    }

    @Test
    @DisplayName("비밀번호 조회 시 비밀번호가 일치하지 않으면 예외가 발생한다.")
    void getMember_WrongPassword_ShouldThrowException() {
        // given
        String loginId = "tester01";
        String wrongPassword = "wrongPassword";
        String encodedPassword = "encodedPassword";
        Member member = Member.builder()
                .loginId(loginId)
                .password(encodedPassword)
                .build();
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(wrongPassword, encodedPassword)).willReturn(false);

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.getMember(loginId, wrongPassword));
    }

    @Test
    @DisplayName("비밀번호 수정 시 정상적으로 수정된다.")
    void updatePassword_ShouldUpdate() {
        // given
        String loginId = "tester01";
        String currentPassword = "OldPassword123!";
        String encodedOldPassword = "encodedOldPassword";
        Member member = Member.builder()
                .loginId(loginId)
                .password(encodedOldPassword)
                .birthDate(LocalDate.of(1990, 1, 1))
                .build();

        MemberCommand.UpdatePassword command = new MemberCommand.UpdatePassword(
                loginId, currentPassword, currentPassword, "NewPassword123!"
        );

        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(currentPassword, encodedOldPassword)).willReturn(true);
        given(passwordEncoder.encode("NewPassword123!")).willReturn("encodedNewPassword");

        // when
        memberService.updatePassword(command);

        // then
        assertThat(member.getPassword()).isEqualTo("encodedNewPassword");
    }

    @Test
    @DisplayName("비밀번호 수정 시 헤더의 비밀번호가 일치하지 않으면 예외가 발생한다.")
    void updatePassword_CurrentPasswordMismatch_ShouldThrowException() {
        // given
        String loginId = "tester01";
        String wrongCurrentPassword = "wrongPassword";
        String encodedOldPassword = "encodedOldPassword";
        Member member = Member.builder()
                .loginId(loginId)
                .password(encodedOldPassword)
                .build();

        MemberCommand.UpdatePassword command = new MemberCommand.UpdatePassword(
                loginId, wrongCurrentPassword, "OldPassword123!", "NewPassword123!"
        );

        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(wrongCurrentPassword, encodedOldPassword)).willReturn(false);

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.updatePassword(command));
    }

    @Test
    @DisplayName("비밀번호 수정 시 신규 비밀번호가 기존과 동일하면 예외가 발생한다.")
    void updatePassword_SamePassword_ShouldThrowException() {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";
        String encodedOldPassword = "encodedOldPassword";
        Member member = Member.builder()
                .loginId(loginId)
                .password(encodedOldPassword)
                .birthDate(LocalDate.of(1990, 1, 1))
                .build();

        MemberCommand.UpdatePassword command = new MemberCommand.UpdatePassword(
                loginId, password, password, password
        );

        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(password, encodedOldPassword)).willReturn(true);

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.updatePassword(command));
    }
}

