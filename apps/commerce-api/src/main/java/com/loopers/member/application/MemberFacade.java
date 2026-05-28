package com.loopers.member.application;

import com.loopers.member.domain.MemberModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class MemberFacade {

    private final MemberService memberService;

    public MemberInfo register(String loginId, String password) {
        return MemberInfo.from(memberService.register(loginId, password));
    }

    @Transactional(readOnly = true)
    public MemberInfo getMyInfo(Long memberId) {
        return MemberInfo.from(memberService.get(memberId));
    }

    public MemberInfo changePassword(Long memberId, String currentPassword, String newPassword) {
        return MemberInfo.from(memberService.changePassword(memberId, currentPassword, newPassword));
    }

    @Transactional(readOnly = true)
    public Long authenticate(String loginId, String password) {
        MemberModel member = memberService.getByLoginId(loginId);
        if (!member.verifyPassword(password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 정보가 올바르지 않습니다.");
        }
        return member.getId();
    }
}
