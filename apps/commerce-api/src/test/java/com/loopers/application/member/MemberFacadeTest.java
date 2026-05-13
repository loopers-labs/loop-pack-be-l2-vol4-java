package com.loopers.application.member;

import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.member.MemberRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
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
        // given
        MemberRequest.SignUp request = new MemberRequest.SignUp(
                "tester01", "Password123!", "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com"
        );

        // when
        memberFacade.signUp(request);

        // then
        verify(memberService).signUp(any());
    }
}
