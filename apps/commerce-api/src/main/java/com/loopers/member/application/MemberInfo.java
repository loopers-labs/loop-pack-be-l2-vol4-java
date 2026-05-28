package com.loopers.member.application;

import com.loopers.member.domain.MemberModel;
import com.loopers.member.domain.MemberStatus;

public record MemberInfo(Long id, String loginId, MemberStatus status) {
    public static MemberInfo from(MemberModel member) {
        return new MemberInfo(member.getId(), member.getLoginId(), member.getStatus());
    }
}
