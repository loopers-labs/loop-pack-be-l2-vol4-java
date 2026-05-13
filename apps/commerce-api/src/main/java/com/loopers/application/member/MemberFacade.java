package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberFacade {
    private final MemberService memberService;

    public MemberInfo join(MemberCommand.Join command) {
        Member member = memberService.join(
                command.loginId(),
                command.loginPassword(),
                command.name(),
                command.birthday(),
                command.email()
        );
        return MemberInfo.from(member);
    }

    public MemberInfo getMember(MemberCommand.GetMember command) {
        return MemberInfo.fromWithMaskedName(memberService.getMember(command.loginId(), command.loginPassword()));
    }
}
