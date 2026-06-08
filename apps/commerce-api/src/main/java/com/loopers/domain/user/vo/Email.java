package com.loopers.domain.user.vo;

import com.loopers.support.Guard;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Email {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Column(name = "email", nullable = false)
    private String value;

    public Email(String value) {
        Guard.notBlank(value, "이메일은 빈값이 들어올 수 없습니다.");
        Guard.matches(value, EMAIL_PATTERN, "이메일 형식이 올바르지 않습니다.");
        this.value = value;
    }
}
