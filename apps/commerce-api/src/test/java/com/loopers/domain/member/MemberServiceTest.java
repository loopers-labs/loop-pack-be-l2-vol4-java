package com.loopers.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

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
