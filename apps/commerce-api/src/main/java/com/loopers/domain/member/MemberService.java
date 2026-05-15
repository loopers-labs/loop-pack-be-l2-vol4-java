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

        String encoded = passwordEncoder.encode(password);
        Password encodedPassword = Password.of(password, birthDate, encoded);
        return memberRepository.save(new Member(loginId, encodedPassword, name, birthDate, email));
    }

    @Transactional
    public void changePassword(String loginId, String oldRawPassword, String newRawPassword) {
        Member member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
        String newEncoded = passwordEncoder.encode(newRawPassword);
        member.changePassword(oldRawPassword, newRawPassword, newEncoded, passwordEncoder);
    }

    @Transactional(readOnly = true)
    public Member getMe(String loginId, String rawPassword) {
        Member member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
        if (!member.matchesPassword(rawPassword, passwordEncoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 올바르지 않습니다.");
        }
        return member;
    }
}
