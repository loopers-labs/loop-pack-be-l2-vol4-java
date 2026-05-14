package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberFacade {

    private final MemberService memberService;

    public void register(String loginId, String password, String name, String birthDate, String email) {
        memberService.register(loginId, password, name, birthDate, email);
    }

    public MemberInfo getMe(String loginId) {
        Member member = memberService.getMe(loginId);
        return MemberInfo.from(member);
    }
}
