package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncryptor passwordEncryptor;

    public Member join(String loginId, String loginPassword, String name, LocalDate birthday, String email){

        if(memberRepository.existsByLoginId(loginId)){
            throw new CoreException(ErrorType.CONFLICT, "이미 사용중인 로그인 아이디입니다.");
        }

        PasswordValidator.validate(loginPassword, birthday);

        Member member = new Member(loginId, passwordEncryptor.encrypt(loginPassword), name, birthday, email);
        return memberRepository.save(member);
    }

    public Member getMember(String loginId, String loginPassword){
        Member member = memberRepository.findByLoginId(loginId).orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."));

        if(!passwordEncryptor.matches(loginPassword, member.getLoginPassword())){
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다.");
        }

        return member;
    }

    public void changePassword(Member member, String oldPassword, String newPassword){

        if(!passwordEncryptor.matches(oldPassword, member.getLoginPassword())){
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }

        if(passwordEncryptor.matches(newPassword, member.getLoginPassword())){
            throw new CoreException(ErrorType.BAD_REQUEST, "이전 비밀번호와 동일하게 설정할 수 없습니다.");
        }

        PasswordValidator.validate(newPassword, member.getBirthday());

        String encryptedPassword = passwordEncryptor.encrypt(newPassword);

        member.changePassword(encryptedPassword);

        memberRepository.save(member);

    }

}
