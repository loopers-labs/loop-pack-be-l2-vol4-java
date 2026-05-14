package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{5,20}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,16}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^([가-힣]{2,20}|[a-zA-Z]{2,20})$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");

    public void signUp(MemberCommand.SignUp command) {
        validateLoginId(command.loginId());
        validateName(command.name());
        validateEmail(command.email());
        validateBirthDate(command.birthDate());

        if (memberRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }

        validatePassword(command.password(), command.birthDate());

        String encryptedPassword = passwordEncoder.encode(command.password());

        Member member = Member.builder()
                .loginId(command.loginId())
                .password(encryptedPassword)
                .name(command.name())
                .birthDate(command.birthDate())
                .email(command.email())
                .build();

        memberRepository.save(member);
    }

    public void updatePassword(MemberCommand.UpdatePassword command) {
        validateLoginId(command.loginId());

        Member member = memberRepository.findByLoginId(command.loginId())
                .orElseThrow(() -> new CoreException(ErrorType.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(command.currentPassword(), member.getPassword())) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }

        if (command.oldPassword().equals(command.newPassword())) {
            throw new CoreException(ErrorType.SAME_PASSWORD_AS_OLD);
        }

        validatePassword(command.newPassword(), member.getBirthDate());

        member.updatePassword(passwordEncoder.encode(command.newPassword()));
    }

    public MemberInfo getMember(String loginId, String password) {
        validateLoginId(loginId);

        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }

        return new MemberInfo(
                member.getLoginId(),
                member.getName(),
                member.getBirthDate(),
                member.getEmail()
        );
    }

    private void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.INVALID_LOGIN_ID);
        }
    }

    private void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.INVALID_NAME);
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.INVALID_EMAIL);
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.REQUIRED_BIRTHDATE);
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.INVALID_BIRTHDATE);
        }
        if (birthDate.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new CoreException(ErrorType.INVALID_BIRTHDATE);
        }
    }

    private void validatePassword(String password, LocalDate birthDate) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD);
        }

        String birthDateStr = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (password.contains(birthDateStr)) {
            throw new CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDATE);
        }
    }
}
