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
    @DisplayName("비밀번호에 생년월일이 포함되면 예외가 발생한다.")
    void signUp_PasswordContainsBirthDate_ShouldThrowException() {
        // given
        MemberCommand.SignUp command = new MemberCommand.SignUp(
                "tester01", "Pass19900101!", "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com"
        );

        // when & then
        assertThrows(RuntimeException.class, () -> memberService.signUp(command));
    }
}
