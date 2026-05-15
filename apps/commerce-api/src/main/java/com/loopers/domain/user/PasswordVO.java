package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Embeddable()
public class PasswordVO {
    private static final DateTimeFormatter FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YEARLESS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter MONTH_DAY_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    private String password;

    protected PasswordVO() {}

    public PasswordVO(String rawPassword, LocalDate birthDate, PasswordEncoder passwordEncoder) {
        validatePassword(rawPassword, birthDate);
        this.password = passwordEncoder.encode(rawPassword);
    }

    private void validatePassword(String password, LocalDate birthDate) {
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (password.length() < 8 || password.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상 16자 이하여야 합니다.");
        }
        validatePasswordNotContainsBirthDate(password, birthDate);
    }

    private void validatePasswordNotContainsBirthDate(String password, LocalDate birthDate) {
        String fullDate = birthDate.format(FULL_DATE_FORMAT);
        String yearlessDate = birthDate.format(YEARLESS_DATE_FORMAT);
        String monthDay = birthDate.format(MONTH_DAY_FORMAT);

        if (password.contains(fullDate) || password.contains(yearlessDate) || password.contains(monthDay)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    public PasswordVO changePassword(String rawCurrentPassword, String rawNewPassword, PasswordEncoder passwordEncoder, LocalDate birthDate) {
        authenticate(rawCurrentPassword, passwordEncoder);
        if (passwordEncoder.matches(rawNewPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        return new PasswordVO(rawNewPassword, birthDate, passwordEncoder);
    }

    public void authenticate(String rawPassword, PasswordEncoder passwordEncoder) {
        if (!passwordEncoder.matches(rawPassword, password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
    }
}
