package com.loopers.domain.member;

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
            throw new RuntimeException("이미 존재하는 아이디입니다.");
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
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(command.currentPassword(), member.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        if (command.oldPassword().equals(command.newPassword())) {
            throw new RuntimeException("기존 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }

        validatePassword(command.newPassword(), member.getBirthDate());

        member.updatePassword(passwordEncoder.encode(command.newPassword()));
    }

    public MemberInfo getMember(String loginId, String password) {
        validateLoginId(loginId);

        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
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
            throw new RuntimeException("로그인 ID 규칙에 맞지 않습니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new RuntimeException("이름은 2~20자의 한글 또는 영문이어야 합니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new RuntimeException("올바른 이메일 형식이 아닙니다.");
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new RuntimeException("생년월일을 입력해주세요.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new RuntimeException("생년월일은 미래 날짜일 수 없습니다.");
        }
        if (birthDate.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new RuntimeException("생년월일이 유효하지 않습니다.");
        }
    }

    private void validatePassword(String password, LocalDate birthDate) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new RuntimeException("비밀번호 규칙에 맞지 않습니다.");
        }

        String birthDateStr = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (password.contains(birthDateStr)) {
            throw new RuntimeException("비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
