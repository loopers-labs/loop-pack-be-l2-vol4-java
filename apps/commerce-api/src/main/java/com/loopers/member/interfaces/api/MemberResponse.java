package com.loopers.member.interfaces.api;

import com.loopers.member.application.MemberInfo;
import com.loopers.member.domain.MemberStatus;

public record MemberResponse(Long id, String loginId, MemberStatus status) {
    public static MemberResponse from(MemberInfo info) {
        return new MemberResponse(info.id(), info.loginId(), info.status());
    }
}
