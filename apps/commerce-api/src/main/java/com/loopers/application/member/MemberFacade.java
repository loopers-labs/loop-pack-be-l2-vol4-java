package com.loopers.application.member;

import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MemberFacade {

    private final MemberService memberService;

    public void signUp(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        memberService.signUp(
                loginId,
                password,
                name,
                birthDate,
                email
        );
    }

    public MemberInfo getMyInfo(String loginId, String password) {
        return memberService.getMember(loginId, password);
    }

    public void updatePassword(
            String loginId,
            String password,
            String oldPassword,
            String newPassword
    ) {
        memberService.updatePassword(
                loginId,
                password,
                oldPassword,
                newPassword
        );
    }
}
