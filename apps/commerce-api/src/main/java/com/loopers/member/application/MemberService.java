package com.loopers.member.application;

import com.loopers.member.domain.Member;
import com.loopers.member.domain.MemberRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public Member register(String loginId, String password) {
        if (memberRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID 입니다.");
        }
        return memberRepository.save(new Member(loginId, password));
    }

    public Member get(Long id) {
        return memberRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 회원을 찾을 수 없습니다."));
    }

    public Member getByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId)
            .orElseThrow(
                () -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 회원을 찾을 수 없습니다."));
    }

    public Member changePassword(Long id, String currentPassword, String newPassword) {
        Member member = get(id);
        member.changePassword(currentPassword, newPassword);
        return memberRepository.save(member);
    }
}
