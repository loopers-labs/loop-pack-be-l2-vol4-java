package com.loopers.application.member;

import com.loopers.domain.member.MemberCommand;
import com.loopers.domain.member.MemberInfo;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.member.MemberRequest;
import com.loopers.interfaces.api.member.MemberResponse;
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

    public MemberResponse.Info getMyInfo(String loginId, String password) {
        MemberInfo memberInfo = memberService.getMember(loginId, password);
        String maskedName = maskName(memberInfo.name());
        return new MemberResponse.Info(
                memberInfo.loginId(),
                maskedName,
                memberInfo.birthDate(),
                memberInfo.email()
        );
    }

    private String maskName(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, name.length() - 1) + "*";
    }
}
