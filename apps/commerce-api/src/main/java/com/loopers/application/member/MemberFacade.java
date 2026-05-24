package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MemberFacade {

    private final MemberService memberService;

    public MemberInfo registerMember(String userId, String password, String email, String userName, String birthDate) {
        MemberModel member = memberService.registerMember(userId, password, email, userName, birthDate);
        return MemberInfo.from(member);
    }

    public MemberInfo getMember(Long id) {
        MemberModel member = memberService.getMember(id);
        return MemberInfo.from(member);
    }

    public void updatePassword(Long id, String currentPassword, String newPassword) {
        memberService.updatePassword(id, currentPassword, newPassword);
    }
}
