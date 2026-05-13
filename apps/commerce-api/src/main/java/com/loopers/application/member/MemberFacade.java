package com.loopers.application.member;

import com.loopers.domain.member.MemberCommand;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.member.MemberRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberFacade {

    private final MemberService memberService;

    public void signUp(MemberRequest.SignUp request) {
        memberService.signUp(new MemberCommand.SignUp(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        ));
    }
}
