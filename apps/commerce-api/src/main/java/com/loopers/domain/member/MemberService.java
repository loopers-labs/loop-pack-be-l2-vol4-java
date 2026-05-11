package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public Member join(String loginId, String loginPassword, String name, LocalDate birthday, String email){

        if(memberRepository.existsByLoginId(loginId)){
            throw new CoreException(ErrorType.CONFLICT, "이미 사용중인 로그인 아이디입니다.");
        }

        Member member = new Member(loginId, loginPassword, name, birthday, email);
        return memberRepository.save(member);
    }

}
