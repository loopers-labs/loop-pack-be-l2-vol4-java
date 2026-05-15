package com.loopers.domain.member;

import com.loopers.application.member.MemberInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        if (memberRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }

        MemberModel.validatePassword(password, birthDate);

        MemberModel member = MemberModel.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(password))
                .name(name)
                .birthDate(birthDate)
                .email(email)
                .build();

        memberRepository.save(member);
    }

    @Transactional
    public void updatePassword(
            String loginId,
            String currentPassword,
            String oldPassword,
            String newPassword
    ) {
        MemberModel.validateLoginId(loginId);

        MemberModel member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }

        if (oldPassword.equals(newPassword)) {
            throw new CoreException(ErrorType.SAME_PASSWORD_AS_OLD);
        }

        member.updatePassword(passwordEncoder.encode(newPassword), newPassword);
    }

    @Transactional(readOnly = true)
    public MemberInfo getMember(String loginId, String password) {
        MemberModel.validateLoginId(loginId);

        MemberModel member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }

        return MemberInfo.from(member);
    }
}
