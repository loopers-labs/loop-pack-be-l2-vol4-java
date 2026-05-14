package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member register(String loginId, String password, String name, String birthDate, String email) {
        memberRepository.findByLoginId(loginId).ifPresent(m -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        });

        Password encodedPassword = Password.of(password, birthDate, passwordEncoder);
        return memberRepository.save(new Member(loginId, encodedPassword, name, birthDate, email));
    }

    @Transactional(readOnly = true)
    public Member getMe(String loginId) {
        return memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
    }
}
