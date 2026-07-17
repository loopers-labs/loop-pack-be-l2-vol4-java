package com.loopers.member.application;

import com.loopers.member.domain.Member;
import com.loopers.member.domain.MemberStatus;

public record MemberInfo(Long id, String loginId, MemberStatus status) {
    public static MemberInfo from(Member member) {
        return new MemberInfo(member.getId(), member.getLoginId(), member.getStatus());
    }
}
