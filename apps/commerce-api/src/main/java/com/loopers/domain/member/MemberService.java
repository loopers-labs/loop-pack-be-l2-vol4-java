package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public void registerMember(String userId, String password, String email, String username, String birthDate) {
        if (memberRepository.existsByUserId(userId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 유저 ID입니다.");
        }
        memberRepository.save(new MemberModel(userId, password, email, username, birthDate));
    }
}