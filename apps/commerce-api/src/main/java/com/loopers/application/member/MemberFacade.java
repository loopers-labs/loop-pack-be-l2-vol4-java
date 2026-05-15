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

    public void changePassword(String loginId, String loginPw, String oldRawPassword, String newRawPassword) {
        memberService.getMe(loginId, loginPw);  // 헤더로 본인 인증
        memberService.changePassword(loginId, oldRawPassword, newRawPassword);
    }

    public MemberInfo getMe(String loginId, String rawPassword) {
        Member member = memberService.getMe(loginId, rawPassword);
        return MemberInfo.from(member);
    }
}
