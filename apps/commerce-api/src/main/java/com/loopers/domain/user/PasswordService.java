package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PasswordService {

    private final PasswordEncoder passwordEncoder;

    public Password encode(RawPassword rawPassword) {
        return new Password(passwordEncoder.encode(rawPassword.getValue()));
    }

    public void validateNotContainBirthDay(RawPassword rawPassword, BirthDay birthDay) {
        if (rawPassword.getValue().contains(birthDay.getValue().replace("-", ""))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비밀번호에 포함될 수 없습니다.");
        }
    }

    public void validateNotSamePassword(RawPassword rawPassword, Password currentPassword) {
        if (passwordEncoder.matches(rawPassword.getValue(), currentPassword.getValue())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호는 사용할 수 없습니다.");
        }
    }
}
