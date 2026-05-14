package com.loopers.application.member;

import com.loopers.domain.member.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberFacadeTest {

    @InjectMocks
    private MemberFacade memberFacade;

    @Mock
    private MemberService memberService;

    @Test
    @DisplayName("회원가입 요청 시 Service의 signUp을 호출한다.")
    void signUp_ShouldCallService() {
        // when
        memberFacade.signUp(
                "tester01", "Password123!", "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com"
        );

        // then
        verify(memberService).signUp(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("내 정보 조회 시 회원 정보를 반환한다.")
    void getMyInfo_ShouldReturnMemberInfo() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        MemberInfo memberInfo = new MemberInfo(
                "tester01",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "tester01@example.com"
        );
        given(memberService.getMember(loginId, password)).willReturn(memberInfo);

        // when
        MemberInfo response = memberFacade.getMyInfo(loginId, password);

        // then
        assertThat(response.loginId()).isEqualTo("tester01");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(response.email()).isEqualTo("tester01@example.com");
    }

    @Test
    @DisplayName("비밀번호 수정 요청 시 Service의 updatePassword를 호출한다.")
    void updatePassword_ShouldCallService() {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";

        // when
        memberFacade.updatePassword(loginId, password, "OldPassword123!", "NewPassword123!");

        // then
        verify(memberService).updatePassword(any(), any(), any(), any());
    }
}
