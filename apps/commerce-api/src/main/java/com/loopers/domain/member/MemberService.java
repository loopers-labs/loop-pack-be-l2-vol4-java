package com.loopers.domain.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,16}$");

    public void signUp(MemberCommand.SignUp command) {
        if (memberRepository.existsByLoginId(command.loginId())) {
            throw new RuntimeException("이미 존재하는 아이디입니다.");
        }

        validatePassword(command);

        Member member = Member.builder()
                .loginId(command.loginId())
                .password(command.password())
                .name(command.name())
                .birthDate(command.birthDate())
                .email(command.email())
                .build();

        memberRepository.save(member);
    }

    private void validatePassword(MemberCommand.SignUp command) {
        if (!PASSWORD_PATTERN.matcher(command.password()).matches()) {
            throw new RuntimeException("비밀번호 규칙에 맞지 않습니다.");
        }

        String birthDateStr = command.birthDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (command.password().contains(birthDateStr)) {
            throw new RuntimeException("비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
